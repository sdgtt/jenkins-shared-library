package sdg.stages
import sdg.Gauntlet
import sdg.stages.IStage
import sdg.NominalException

/**
 * This class represents the "LinuxTests" stage in the Jenkins pipeline.
 * It contains methods related to running Linux tests on the board.
 */
class LinuxTests implements IStage {
    
    /**
     * Retrieves the name of the stage.
     *
     * @return A string representing the name of the stage, which is "LinuxTests".
     */
    String getStageName(){
        return "LinuxTests"
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
     * Executes the steps for the LinuxTests stage.
     *
     * @param gauntlet The Gauntlet instance used to execute the stage.
     * @param board The name of the board on which the Linux tests are being run.
     */
    void stageSteps(Gauntlet gauntlet, String board){
        def logger = gauntlet.logger
        def gauntEnv = gauntlet.gauntEnv
        def steps = gauntlet.stepExecutor
        
        logger.info("Running ${getStageName()} for ${board}")
        def failed_test = ''
        def devs = []
        def missing_devs = []
        try {
            // run_i('pip3 install pylibiio',true)
            //def ip = nebula('uart.get-ip')
            def ip = gauntlet.nebula('update-config network-config dutip --board-name='+board)

            try{
                gauntlet.nebula('driver.check-iio-devices --uri="ip:'+ip+'" --board-name='+board, true, true, true)
            }catch(Exception ex) {
                failed_test = failed_test + "[iio_devices check failed: ${ex.getMessage()}]"
                missing_devs = Eval.me(ex.getMessage().split('\n').last().split('not found')[1].replaceAll("'\$",""))
                steps.writeFile(file: board+'_missing_devs.log', text: missing_devs.join("\n"))
                gauntlet.set_elastic_field(board, 'drivers_missing', missing_devs.size().toString())
            }
            // get drivers enumerated
            devs = Eval.me(gauntlet.nebula('update-config driver-config iio_device_names -b '+board, false, true, false))
            devs = devs.minus(missing_devs)
            steps.writeFile(file: board+'_enumerated_devs.log', text: devs.join("\n"))
            gauntlet.set_elastic_field(board, 'drivers_enumerated', devs.size().toString())

            try{
                steps.sh('iio_info --uri=ip:'+ip)
                gauntlet.nebula("net.check-dmesg --ip='"+ip+"' --board-name="+board)
            }catch(Exception ex) {
                failed_test = failed_test + "[dmesg check failed: ${ex.getMessage()}]"
            }
            
            try{
                if (!gauntEnv.firmware_boards.contains(board)){
                    try{
                        gauntlet.nebula('update-config board-config serial --board-name='+board)
                        gauntlet.nebula("net.run-diagnostics --ip='"+ip+"' --board-type=rpi --board-name="+board, true, true, true)
                    }catch(Exception ex){
                        gauntlet.nebula("net.run-diagnostics --ip='"+ip+"' --board-name="+board, true, true, true)
                    }
                    steps.archiveArtifacts artifacts: '*_diag_report.tar.bz2', followSymlinks: false, allowEmptyArchive: true
                }
            }catch(Exception ex) {
                failed_test = failed_test + " [diagnostics failed: ${ex.getMessage()}]"
            }

            if(failed_test && !failed_test.allWhitespace){
                steps.unstable("Linux Tests Failed: ${failed_test}")
            }
        }catch(Exception ex) {
            throw new NominalException(ex.getMessage())
        }finally{
            // count dmesg errs and warns
            gauntlet.set_elastic_field(board, 'dmesg_errs', steps.sh(returnStdout: true, script: 'cat dmesg_err_filtered.log | wc -l').trim())
            gauntlet.set_elastic_field(board, 'dmesg_warns', steps.sh(returnStdout: true, script: 'cat dmesg_warn.log | wc -l').trim())
            // Rename logs
            gauntlet.run_i("if [ -f dmesg.log ]; then mv dmesg.log dmesg_" + board + ".log; fi")
            gauntlet.run_i("if [ -f dmesg_err_filtered.log ]; then mv dmesg_err_filtered.log dmesg_" + board + "_err.log; fi")
            gauntlet.run_i("if [ -f dmesg_warn.log ]; then mv dmesg_warn.log dmesg_" + board + "_warn.log; fi")
            steps.archiveArtifacts artifacts: '*.log', followSymlinks: false, allowEmptyArchive: true
        }
        
    }
}
