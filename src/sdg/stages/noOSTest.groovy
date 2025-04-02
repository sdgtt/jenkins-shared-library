package sdg.stages
import sdg.Gauntlet
import sdg.stages.IStage
import sdg.NominalException

/**
 * This class represents the "noOSTest" stage in the Jenkins pipeline.
 * It contains methods related to running tests on the board without an OS.
 */
class noOSTest implements IStage {

    /**
     * Retrieves the name of the stage.
     *
     * @return A string representing the name of the stage, which is "noOSTest".
     */
    String getStageName(){
        return "noOSTest"
    }

    /**
     * Returns a closure that executes a stage in the Jenkins pipeline.
     *
     * @return Closure that takes two parameters: gauntlet and board
     *         The closure executes a stage using the gauntlet's stepExecutor and calls the stageSteps method.
     *
     * @param gauntlet The gauntlet object that contains the stepExecutor.
     * @param board The board parameter to be passed to the stageSteps method.
     */
    Closure getCls(){
        return { gauntlet, board ->
            gauntlet.stepExecutor.stage(getStageName()){
                stageSteps(gauntlet, board)
            }
        }
    }

    /**
     * Executes the steps for the noOSTest stage.
     *
     * @param gauntlet The Gauntlet instance used to execute the stage.
     * @param board The name of the board on which the tests are being run.
     */
    void stageSteps(Gauntlet gauntlet, String board){
        def logger = gauntlet.logger
        def gauntEnv = gauntlet.gauntEnv
        def steps = gauntlet.stepExecutor
        
        logger.info("Running ${getStageName()} for ${board}")
        steps.stage("Run Tests"){
            RunTests(gauntlet, board, DownloadBinaries(gauntlet, board))
        }
    }

    /**
     * Downloads the binaries for the board.
     *
     * @param gauntlet The Gauntlet instance used to execute the stage.
     * @param board The name of the board for which the binaries are being downloaded.
     * @return The filepath of the downloaded binaries.
     */
    String DownloadBinaries(Gauntlet gauntlet, String board){
        def logger = gauntlet.logger
        def gauntEnv = gauntlet.gauntEnv
        def steps = gauntlet.stepExecutor

        def example = gauntlet.nebula('update-config board-config example --board-name='+board)
        def platform = gauntlet.nebula('update-config downloader-config platform --board-name='+board)
        def filepath = ''
        logger.info("Downloading binaries for ${board}")
        gauntlet.nebula('dl.bootfiles --board-name=' + board + ' --source-root="' + gauntEnv.nebula_local_fs_source_root + '" --source=' + gauntEnv.bootfile_source
                                    +  ' --branch="' + gauntEnv.hdlBranch.toString() +  '" --filetype="noos"', true, true, true)
        def binaryfiles = steps.sh (script: "ls outs", returnStdout: true).trim()
        logger.info("binary files: " + binaryfiles)
        def found = false;
        for (String binaryfile : binaryfiles.split("\\r?\\n")) {
            def carrier = board.split('_')[0]
            def daughter = board.split('_')[1]
            if (daughter.contains('-')){
                daughter = daughter.split('-')[0]
            }
            if (binaryfile.contains(example) && binaryfile.contains(carrier) && binaryfile.contains(daughter)){
                if (platform == "Xilinx"){
                    def bootgen = 'outs/'+binaryfile+'/bootgen_sysfiles.tar.gz'
                    steps.sh(script: 'tar -xf '+bootgen)
                    filepath = steps.sh(script: 'ls | grep *'+carrier+'.elf', returnStdout: true).trim()
                    logger.info("File/filepath: "+filepath) 
                    found = true;
                    break
                }else {
                    if (binaryfile.contains('.elf')) {
                        filepath = 'outs/'+binaryfile
                        logger.info("File/filepath: "+filepath) 
                        found = true;
                        break
                    }
                }
            }
        }
        if (!found) {
            //for now, stop test pipeline if file is not found
            throw new Exception("No elf found for "+board)
        }
        return filepath
    }

    /**
     * Runs the tests on the board.
     *
     * @param gauntlet The Gauntlet instance used to execute the stage.
     * @param board The name of the board on which the tests are being run.
     * @param filepath The filepath of the downloaded binaries.
     */
    void RunTests(Gauntlet gauntlet, String board, String filepath){
        def logger = gauntlet.logger
        def gauntEnv = gauntlet.gauntEnv
        def steps = gauntlet.stepExecutor

        logger.info("Running tests for ${board}")
        def project = gauntlet.nebula('update-config downloader-config no_os_project --board-name='+board)
        def jtag_cable_id = gauntlet.nebula('update-config jtag-config jtag_cable_id --board-name='+board)
        def serial = gauntlet.nebula('update-config uart-config address --board-name='+board)
        def baudrate = gauntlet.nebula('update-config uart-config baudrate --board-name='+board)
        def platform = gauntlet.nebula('update-config downloader-config platform --board-name='+board)
        def example = gauntlet.nebula('update-config board-config example --board-name='+board)
        def screen_baudrate
        
        if (gauntEnv.vivado_ver == '2020.1' || gauntEnv.vivado_ver == '2021.1' ){
                steps.sh 'ln /usr/bin/make /usr/bin/gmake'
        }
        if (example.contains('iio')){
            screen_baudrate = gauntEnv.iio_uri_baudrate
        } else {
            screen_baudrate = baudrate
        }
        steps.sh 'screen -S ' +board+ ' -dm -L -Logfile ' +board+'-boot.log ' +serial+ ' '+screen_baudrate
        if (platform == "Xilinx"){
            steps.sh 'git clone --depth=1 -b '+gauntEnv.no_os_branch+' '+gauntEnv.no_os_repo
            steps.sh 'cp '+filepath+ ' no-OS/projects/'+ project +'/'
            steps.sh 'cp *.xsa no-OS/projects/'+ project +'/system_top.xsa'
            steps.dir('no-OS/projects/'+project){
                steps.sh 'source /opt/Xilinx/Vivado/' +gauntEnv.vivado_ver+ '/settings64.sh && make run' +' JTAG_CABLE_ID='+jtag_cable_id
            }
        } else {
            gauntlet.run_i('wget https://raw.githubusercontent.com/analogdevicesinc/no-OS/'+gauntEnv.no_os_branch+'/tools/scripts/mcufla.sh', true)
            steps.sh 'chmod +x mcufla.sh'
            def cmd = './mcufla.sh ' +filepath+' '+jtag_cable_id
            def flashStatus = steps.sh (returnStatus: true, script: cmd)
            if ((flashStatus != 0)){
                throw new Exception("Flashing binary file failed.")
            }                   
        }
        steps.sleep(180) //wait to fully boot
        steps.archiveArtifacts artifacts: "*-boot.log", followSymlinks: false, allowEmptyArchive: true
        steps.sh 'screen -XS '+board+ ' kill'
        if (example.contains('iio')){
            steps.retry(3){
                logger.info("---------------------------")
                steps.sleep(10);
                logger.info("Check context")
                def cmd = 'iio_info -u serial:' + serial + ',' +baudrate+ ' &> '+board+'-iio_info.log'
                def ret = steps.sh (returnStatus: true, script: cmd)
                steps.archiveArtifacts artifacts: "*-iio_info.log", followSymlinks: false, allowEmptyArchive: true
                if (ret != 0){
                    throw new Exception("Failed.")
                }
            }
        }
    }
}