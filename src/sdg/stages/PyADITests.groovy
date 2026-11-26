package sdg.stages
import sdg.Gauntlet
import sdg.stages.IStage

/**
 * The PyADITests class implements the IStage interface
 * and provides functionality to run pyadi-iio tests on the target board.
 */
class PyADITests implements IStage {
    /**
     * Returns the name of the stage.
     *
     * @return The name of the stage, which is "PyADITests".
     */
    String getStageName(){
        return "PyADITests"
    }

    /**
  
     */
    Closure getCls(){
        return { gauntlet, board ->
            gauntlet.stepExecutor.stage(getStageName()){
                stageSteps(gauntlet, board)
            }
        }
    }
    /**
     * Executes the steps for the PyADITests stage.
     *
     * @param gauntlet The Gauntlet instance used to execute the stage.
     * @param board The name of the board to be power cycled.
     */
    void stageSteps(Gauntlet gauntlet, String board){
        def logger = gauntlet.logger
        def gauntEnv = gauntlet.gauntEnv
        def steps = gauntlet.stepExecutor

        logger.info("Running ${getStageName()} for ${board}")
        try
        {
            //def ip = nebula('uart.get-ip')
            def ip;
            def serial;
            def baudrate;
            def uri;
            def description = ""
            def pytest_attachment = null
            logger.info('IP: ' + ip)
            // temporarily get pytest-libiio from another source
            gauntlet.run_i('git clone -b "' + gauntEnv.pytest_libiio_branch + '" ' + gauntEnv.pytest_libiio_repo, true)
            steps.dir('pytest-libiio'){
                gauntlet.run_i('pip3 install .', true)
            }
            //install libad9361 python bindings
            try{
                steps.sh 'python3 -c "import ad9361"'
            }catch (Exception ex){
                gauntlet.run_i('sudo rm -rf libad9361-iio')
                gauntlet.run_i('git clone -b '+ gauntEnv.libad9361_iio_branch + ' ' + gauntEnv.libad9361_iio_repo, true)
                steps.dir('libad9361-iio'){
                    steps.sh('mkdir -p build')
                    steps.dir('build'){
                        steps.sh('sudo cmake -DPYTHON_BINDINGS=ON ..')
                        steps.sh('sudo make')
                        steps.sh('sudo make install')
                        steps.sh('ldconfig')
                    }
                }
            }
            //scm pyadi-iio
            steps.dir('pyadi-iio'){
                def result = gauntlet.isMultiBranchPipeline(gauntEnv.pyadi_iio_repo)
                result.branch = !result.isMultiBranch ? gauntEnv.pyadi_iio_branch : result.branch
                def cloneConfigs = [credentialsId: '', url: gauntEnv.pyadi_iio_repo]
                cloneConfigs['refspec'] = result.isMultiBranch ? result.ref : cloneConfigs['refspec']
                steps.checkout([
                    $class : 'GitSCM',
                    branches : [[name: result.branch]],
                    userRemoteConfigs: [cloneConfigs]
                    ])
                }

            steps.dir('pyadi-iio')
                {
                gauntlet.run_i('pip3 install -r requirements.txt', true)
                gauntlet.run_i('pip3 install -r requirements_dev.txt', true)
                gauntlet.run_i('pip3 install pylibiio', true)
                gauntlet.run_i('mkdir testxml')
                gauntlet.run_i('mkdir testhtml')
                if (gauntEnv.iio_uri_source == "ip"){
                    ip = gauntlet.nebula('update-config network-config dutip --board-name='+board)
                    uri = "ip:" + ip;
                }else{
                    serial = gauntlet.nebula('update-config uart-config address --board-name='+board)
                    baudrate = gauntlet.nebula('update-config uart-config baudrate --board-name='+board)
                    uri = "serial:" + serial + "," + baudrate
                }
                def check = gauntlet.check_for_marker(board)
                board = board.replaceAll('-', '_')
                def board_name = check.board_name.replaceAll('-', '_')
                def marker = check.marker
                def cmd = "python3 -m pytest --html=testhtml/report.html --junitxml=testxml/" + board + "_reports.xml"
                cmd += " --adi-hw-map -v -k 'not stress and not prod' -s --uri="+uri+" -m " + board_name
                cmd += " --scan-verbose --capture=tee-sys" + marker
                def statusCode = steps.sh(script:cmd, returnStatus:true)

                // generate html report
                if (steps.fileExists('testhtml/report.html')){
                    steps.publishHTML(target : [
                        escapeUnderscores: false, 
                        allowMissing: false, 
                        alwaysLinkToLastBuild: false, 
                        keepAll: true, 
                        reportDir: 'testhtml', 
                        reportFiles: 'report.html', 
                        reportName: board, 
                        reportTitles: board])
                    }

                // get pytest results for logging
                def xmlFile = 'testxml/' + board + '_reports.xml'
                if(steps.fileExists(xmlFile)){
                    try{
                        gauntlet.parseForLogging ('pytest', xmlFile, board)
                    }catch(Exception ex){
                        logger.info('Parsing pytest results failed')
                        logger.info(gauntlet.getStackTrace(ex))
                    }
                    pytest_attachment = board+"_reports.xml"
                }
                            
                // throw exception if pytest failed
                if ((statusCode != 5) && (statusCode != 0)){
                // Ignore error 5 which means no tests were run
                    steps.unstable("PyADITests Failed")
                }                
            }
        }
        finally
        {
            steps.archiveArtifacts artifacts: 'pyadi-iio/testxml/*.xml', followSymlinks: false, allowEmptyArchive: true
            steps.junit testResults: 'pyadi-iio/testxml/*.xml', allowEmptyResults: true                    
        }
    }
}