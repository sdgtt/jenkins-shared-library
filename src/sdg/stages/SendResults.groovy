package sdg.stages
import sdg.Gauntlet
import sdg.stages.IStage

/**
 * The SendResults class implements the IStage interface and provides
 * functionality to send results of a stage execution.
 */
class SendResults implements IStage {
    /**
     * Returns the name of the stage.
     *
     * @return The name of the stage, which is "SendResults".
     */
    String getStageName(){
        return "SendResults"
    }

    
    Closure getCls(){
        return { gauntlet, board ->
            gauntlet.stepExecutor.stage(getStageName()){
                stageSteps(gauntlet, board)
            }
        }
    }
    
    void stageSteps(Gauntlet gauntlet, String board){
        def logger = gauntlet.logger
        def gauntEnv = gauntlet.gauntEnv
        def steps = gauntlet.stepExecutor

        logger.info("Running ${getStageName()} for ${board}")
        def is_hdl_release = "False"
        def is_linux_release = "False"
        def is_boot_partition_release = "False"
        def cmd = ""
        if (gauntEnv.bootPartitionBranch == 'NA'){
            is_hdl_release = ( gauntEnv.hdlBranch == "release" )? "True": "False"
            is_linux_release = ( gauntEnv.linuxBranch == "release" )? "True": "False"
        }else{
            is_boot_partition_release = ( gauntEnv.bootPartitionBranch == "release" )? "True": "False"
        }
        steps.println(gauntEnv.elastic_logs)
        logger.info("Starting send log to elastic search")
        cmd = 'boot_folder_name ' + board
        cmd += ' hdl_hash ' + '\'' + gauntlet.get_elastic_field(board, 'hdl_hash' , 'NA') + '\''
        cmd += ' linux_hash ' +  '\'' + gauntlet.get_elastic_field(board, 'linux_hash' , 'NA') + '\''
        cmd += ' boot_partition_hash ' + '\'' + gauntEnv.boot_partition_hash + '\''
        cmd += ' hdl_branch ' + gauntEnv.hdlBranch
        cmd += ' linux_branch ' + gauntEnv.linuxBranch
        cmd += ' boot_partition_branch ' + gauntEnv.bootPartitionBranch
        cmd += ' is_hdl_release ' + is_hdl_release
        cmd += ' is_linux_release '  +  is_linux_release
        cmd += ' is_boot_partition_release ' + is_boot_partition_release
        cmd += ' uboot_reached ' + gauntlet.get_elastic_field(board, 'uboot_reached', 'False')
        cmd += ' linux_prompt_reached ' + gauntlet.get_elastic_field(board, 'linux_prompt_reached', 'False')
        cmd += ' drivers_enumerated ' + gauntlet.get_elastic_field(board, 'drivers_enumerated', '0')
        cmd += ' drivers_missing ' + gauntlet.get_elastic_field(board, 'drivers_missing', '0')
        cmd += ' dmesg_warnings_found ' + gauntlet.get_elastic_field(board, 'dmesg_warns' , '0')
        cmd += ' dmesg_errors_found ' + gauntlet.get_elastic_field(board, 'dmesg_errs' , '0')
        // cmd +="jenkins_job_date datetime.datetime.now(),
        cmd += ' jenkins_build_number ' + steps.getEnv().BUILD_NUMBER
        cmd += ' jenkins_project_name ' + '\'' + steps.getEnv().JOB_NAME + '\''
        cmd += ' jenkins_agent ' + steps.getEnv().NODE_NAME
        cmd += ' jenkins_trigger ' + gauntEnv.job_trigger
        cmd += ' pytest_errors ' + gauntlet.get_elastic_field(board, 'pytest_errors', '0')
        cmd += ' pytest_failures ' + gauntlet.get_elastic_field(board, 'pytest_failures', '0')
        cmd += ' pytest_skipped ' + gauntlet.get_elastic_field(board, 'pytest_skipped', '0')
        cmd += ' pytest_tests ' + gauntlet.get_elastic_field(board, 'pytest_tests', '0')
        cmd += ' matlab_errors ' + gauntlet.get_elastic_field(board, 'matlab_errors', '0')
        cmd += ' matlab_failures ' + gauntlet.get_elastic_field(board, 'matlab_failures', '0')
        cmd += ' matlab_skipped ' + gauntlet.get_elastic_field(board, 'matlab_skipped', '0')
        cmd += ' matlab_tests ' + gauntlet.get_elastic_field(board, 'matlab_tests', '0')
        cmd += ' last_failing_stage ' + gauntlet.get_elastic_field(board, 'last_failing_stage', 'NA')
        cmd += ' last_failing_stage_failure ' + gauntlet.get_elastic_field(board, 'last_failing_stage_failure', 'NA')
        gauntlet.sendLogsToElastic(cmd)
    }
}        