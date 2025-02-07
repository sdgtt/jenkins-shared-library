package sdg.stages
import sdg.Gauntlet
import sdg.stages.IStage
import sdg.NominalException
import org.jenkinsci.plugins.pipeline.modeldefinition.Utils

/**
 * This class represents the "UpdateBOOTFiles" stage in the Jenkins pipeline.
 * It contains methods related to updating BOOT files.
 */
class UpdateBOOTFiles implements IStage {
    
    /**
     * Retrieves the name of the stage.
     *
     * @return A string representing the name of the stage, which is "UpdateBOOTFiles".
     */
    String getStageName(){
        return "UpdateBOOTFiles"
    }

    /**
     * Returns a closure that executes a stage in the Jenkins pipeline.
     *
     * @return Closure that takes three parameters: gauntlet, board, and an optional ml_bootbin_case.
     *         The closure executes a stage using the gauntlet's stepExecutor and calls the stageSteps method.
     *
     * @param gauntlet The gauntlet object that contains the stepExecutor.
     * @param board The board parameter to be passed to the stageSteps method.
     * @param ml_bootbin_case Optional parameter to be passed to the stageSteps method.
     */
    Closure getCls(){
        return { gauntlet, board, ml_bootbin_case=null ->
            gauntlet.stepExecutor.stage(getStageName()){
                stageSteps(gauntlet, board, ml_bootbin_case)
            }
        }
    }

    /**
     * Executes the steps for the UpdateBOOTFiles stage.
     *
     * @param gauntlet The Gauntlet instance used to execute the stage.
     * @param board The name of the board for which the BOOT files are being updated.
     * @param ml_bootbin_case The case identifier for the ML boot binary.
     */
    void stageSteps(Gauntlet gauntlet, String board, String ml_bootbin_case){
        def logger = gauntlet.logger
        def gauntEnv = gauntlet.gauntEnv
        logger.info("Running ${getStageName()} for ${board}")
        try{
            def boolean trxPluto = gauntEnv.docker_args.contains("MATLAB") && (board=="pluto")
            if (trxPluto){
                logger.info("Skip pluto firmware update.")
                Utils.markStageSkippedForConditional(getStageName())
            }else{
                logger.info("Board name passed: "+board)
                logger.info("Branch: " + gauntEnv.branches.toString())
                try{
                    if (gauntEnv.toolbox_generated_bootbin) {
                        logger.info("MATLAB BOOT.BIN job variation: "+ml_bootbin_case)
                        logger.info("Downloading bootbin generated from toolbox")
                        gauntlet.nebula('show-log dl.matlab-bootbins'+
                                ' -t "'+gauntEnv.ml_toolbox+
                            '" -b "'+gauntEnv.ml_branch+
                            '" -u "'+gauntEnv.ml_build+'"')
                    }
                    if (board=="pluto"){
                        if (gauntEnv.firmwareVersion == 'NA')
                            throw new Exception("Firmware must be specified")
                        gauntlet.nebula('dl.bootfiles --board-name=' + board 
                                + ' --source="github"'
                                +  ' --branch="' + gauntEnv.firmwareVersion  
                                +  '" --filetype="firmware"', true, true, true)
                    }else{
                        if (gauntEnv.branches == ["NA","NA"])
                            throw new Exception("Either hdl_branch/linux_branch or boot_partition_branch must be specified")
                        if (gauntEnv.bootfile_source == "NA")
                            throw new Exception("bootfile_source must be specified")
                        def cmd = 'dl.bootfiles --board-name=' + board
                        cmd += ' --source-root=' + gauntEnv.nebula_local_fs_source_root
                        cmd += ' --source=' + gauntEnv.bootfile_source
                        cmd += ' --branch=' + gauntEnv.branches.toString()
                        cmd += (gauntEnv.url_template == 'NA')? "" : ' --url-template=' + gauntEnv.url_template
                        cmd += gauntEnv.filetype
                        gauntlet.nebula(cmd, true, true, true)
                    }
                    //get git sha properties of files
                    gauntlet.get_gitsha(board)
                }catch(Exception ex){
                    throw new Exception('Downloader error: '+ ex.getMessage()) 
                }

                if(gauntEnv.toolbox_generated_bootbin) {
                    logger.info("Replace bootbin with one generated from toolbox")
                    // Get list of files in ml_bootbins folder
                    def ml_bootfiles = sh (script: "ls   ml_bootbins", returnStdout: true).trim()
                    logger.info("ml_bootfiles: " + ml_bootfiles)
                    // Filter bootbin for specific case (rx,tx,rxtx)
                    def found = false;
                    for (String bootfile : ml_bootfiles.split("\\r?\\n")) {
                        logger.info("Inspecting " + bootfile + " for " + ml_bootbin_case + "_BOOT.BIN")
                        logger.info("Must contain board: " + board)
                        logger.info(bootfile.contains(board) && bootfile.contains("_"+ml_bootbin_case+"_BOOT.BIN"))
                        if (bootfile.contains(board) && bootfile.contains("_"+ml_bootbin_case+"_BOOT.BIN")) {
                            // Copy bootbin to outs folder
                            logger.info("Copy " + bootfile + " to outs folder")
                            sh "cp ml_bootbins/${bootfile} outs/BOOT.BIN"
                            found = true;
                            break
                        }
                    }
                    if (!found) {
                        logger.info("No bootbin found for " + ml_bootbin_case + " case")
                        logger.info("Skipping Update BOOT Files stage")
                        logger.info("Skipping "+gauntEnv.ml_test_stages.toString()+" related test stages")
                        gauntEnv.internal_stages_to_skip[board] = gauntEnv.ml_test_stages;
                        return;
                    }
                }

                //update-boot-files
                gauntlet.nebula('manager.update-boot-files --board-name=' + board + ' --folder=outs', true, true, true)
                if (board=="pluto"){
                    gauntlet.stepExecutor.retry(2){
                        sleep(50)
                        gauntlet.nebula('uart.set-local-nic-ip-from-usbdev --board-name=' + board)
                    }
                }

                gauntlet.set_elastic_field(board, 'uboot_reached', 'True')
                gauntlet.set_elastic_field(board, 'kernel_started', 'True')
                gauntlet.set_elastic_field(board, 'linux_prompt_reached', 'True')
                gauntlet.set_elastic_field(board, 'post_boot_failure', 'False')

                // verify checksum
                gauntlet.nebula('manager.verify-checksum --board-name=' + board + ' --folder=outs', true, true, true)
            }
        }catch(Exception ex){

            def is_nominal_exception = false
            if (ex.getMessage().contains('u-boot not reached')){
                gauntlet.set_elastic_field(board, 'uboot_reached', 'False')
                gauntlet.set_elastic_field(board, 'kernel_started', 'False')
                gauntlet.set_elastic_field(board, 'linux_prompt_reached', 'False')
            }else if (ex.getMessage().contains('u-boot menu cannot boot kernel')){
                gauntlet.set_elastic_field(board, 'uboot_reached', 'True')
                gauntlet.set_elastic_field(board, 'kernel_started', 'False')
                gauntlet.set_elastic_field(board, 'linux_prompt_reached', 'False')
            }else if (ex.getMessage().contains('Linux not fully booting')){
                gauntlet.set_elastic_field(board, 'uboot_reached', 'True')
                gauntlet.set_elastic_field(board, 'kernel_started', 'True')
                gauntlet.set_elastic_field(board, 'linux_prompt_reached', 'False')
            }else if (ex.getMessage().contains('Linux is functional but Ethernet is broken after updating boot files') ||
                        ex.getMessage().contains('SSH not working but ping does after updating boot files') ||
                        ex.getMessage().contains('Checksum does not match')){
                gauntlet.set_elastic_field(board, 'uboot_reached', 'True')
                gauntlet.set_elastic_field(board, 'kernel_started', 'True')
                gauntlet.set_elastic_field(board, 'linux_prompt_reached', 'True')
                gauntlet.set_elastic_field(board, 'post_boot_failure', 'True')
            }else if (ex.getMessage().contains('Downloader error')){
                gauntlet.set_elastic_field(board, 'uboot_reached', 'False')
                gauntlet.set_elastic_field(board, 'kernel_started', 'False')
                gauntlet.set_elastic_field(board, 'linux_prompt_reached', 'False')
                is_nominal_exception = true
            }else{
                logger.error("Update BOOT Files unexpectedly failed. ${ex.getMessage()}")
            }
            gauntlet.get_gitsha(board)
            def failing_msg = "'" + ex.getMessage().split('\n').last().replaceAll( /(['])/, '"') + "'"
            // send logs to elastic
            if (gauntEnv.send_results){
                gauntlet.set_elastic_field(board, 'last_failing_stage', 'UpdateBOOTFiles')
                gauntlet.set_elastic_field(board, 'last_failing_stage_failure', failing_msg)
                stage_library('SendResults').call(this, board)
            }
            if (is_nominal_exception)
                throw new NominalException('UpdateBOOTFiles failed: '+ ex.getMessage())
            throw new Exception('UpdateBOOTFiles failed: '+ ex.getMessage())

        }finally{

            //archive uart logs
            gauntlet.run_i("if [ -f ${board}.log ]; then mv ${board}.log uart_boot_" + board + ".log; fi")
            gauntlet.stepExecutor.archiveArtifacts artifacts: 'uart_boot_*.log', followSymlinks: false, allowEmptyArchive: true

        }
    }
}
