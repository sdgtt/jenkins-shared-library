package sdg.stages
import sdg.Gauntlet
import sdg.stages.IStage
import sdg.NominalException

/**
 * This class represents the "RecoverBoard" stage in the Jenkins pipeline.
 * It contains methods related to recovering the board.
 */
class RecoverBoard implements IStage {
    
    /**
     * Retrieves the name of the stage.
     *
     * @return A string representing the name of the stage, which is "RecoverBoard".
     */
    String getStageName(){
        return "RecoverBoard"
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
     * Executes the steps for the RecoverBoard stage.
     *
     * @param gauntlet The Gauntlet instance used to execute the stage.
     * @param board The name of the board for which the BOOT files are being updated.
     */
    void stageSteps(Gauntlet gauntlet, String board){
        def logger = gauntlet.logger
        def gauntEnv = gauntlet.gauntEnv
        def steps = gauntlet.stepExecutor
        
        logger.info("Running ${getStageName()} for ${board}")
        def ref_branch = []
        def nebula_cmd = 'manager.recovery-device-manager --board-name=' + board + ' --folder=outs'
        switch(gauntEnv.recovery_ref){
            case "SD":
                nebula_cmd = nebula_cmd + ' --sdcard'
                ref_branch = 'release'
                break;
            case "boot_partition_master":
                ref_branch = 'master'
                break;
            case "boot_partition_release":
                ref_branch = 'release'
                break;
            default:
                throw new Exception('Unknown recovery ref branch: ' + gauntEnv.recovery_ref)
        }
        if (board=="pluto"){
            logger.warning("Recover stage does not support pluto yet!")
        }else{
            if (gauntEnv.bootfile_source == "NA")
                throw new Exception("bootfile_source must be specified")

            // confirm if indeed the board is dead and needs recovery
            def to_proceed = false
            try{
                gauntlet.nebula('net.check-board-booted --board-name=' + board)
                logger.info('Board is booted, no need for recovery')
            }catch(Exception ex){
                to_proceed = true
            }
            if(to_proceed){
                try{
                    logger.info("Fetching reference boot files")
                    gauntlet.nebula('dl.bootfiles --board-name=' + board 
                        + ' --source-root="' + gauntEnv.nebula_local_fs_source_root 
                        + '" --source=' + gauntEnv.bootfile_source
                        +  ' --branch="' + ref_branch.toString()
                        +  '" --filetype="boot_partition"', true, true, true)

                    logger.info("Extracting reference fsbl and u-boot")
                    steps.sh("cp outs/bootgen_sysfiles.tgz .")
                    steps.sh("tar -xzvf bootgen_sysfiles.tgz; cp u-boot*.elf u-boot.elf")
                    logger.info("Executing board recovery...")
                    gauntlet.nebula(nebula_cmd)
                }catch(Exception ex){
                    if(gauntEnv.netbox_allow_disable){
                        def message = "Disabled by ${gauntEnv.env.JOB_NAME} ${gauntEnv.env.BUILD_NUMBER}"
                        def disable_command = 'netbox.disable-board --board-name=' + board + ' --failure --reason=' + '"' + message + '"' + ' --power-off'
                        gauntlet.nebula(disable_command)
                    }
                    logger.error(gauntlet.getStackTrace(ex))
                    throw ex
                }finally{
                    //archive uart logs
                    gauntlet.run_i("if [ -f recovery/${board}.log ]; then mv recovery/${board}.log uart_recover_" + board + ".log; fi")
                    steps.archiveArtifacts artifacts: 'uart_recover_*.log', followSymlinks: false, allowEmptyArchive: true
                }                
            }
        }
    }
}
