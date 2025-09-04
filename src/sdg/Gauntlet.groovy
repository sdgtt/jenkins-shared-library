package sdg

import sdg.FailSafeWrapper
import sdg.NominalException
import sdg.Logger
import sdg.ioc.*
import org.jenkinsci.plugins.pipeline.modeldefinition.Utils
import com.cloudbees.groovy.cps.NonCPS

/** A map that holds all constants and data members that can be override when constructing  */
gauntEnv

/** context */
isDefaultContext

/** steps */
stepExecutor

/** logger */
logger

/**
 * Imitates a constructor
 * Defines an instance of Consul object. All according to api
 * @param hdlBranch - String of name of hdl branch to use for bootfile source
 * @param linuxBranch - String of name of linux branch to use for bootfile source
 * @param bootPartitionBranch - String of name of boot partition branch to use for bootfile source, set to 'NA' if hdl and linux is to be used
 * @param firmwareVersion - String of name of firmware version branch to use for pluto and m2k
 * @param bootfile_source - String location of bootfiles. Options: sftp, artifactory, http, local
 * @return constructed object
 */
def construct(hdlBranch, linuxBranch, bootPartitionBranch, firmwareVersion, bootfile_source) {
    // initialize gauntEnv
    isDefaultContext = ContextRegistry.getContext().isDefault()
    stepExecutor = ContextRegistry.getContext().getStepExecutor()
    logger = new Logger(this)
    gauntEnv = stepExecutor.getGauntEnv(hdlBranch, linuxBranch, bootPartitionBranch, firmwareVersion, bootfile_source)
    gauntEnv.agents_online = getOnlineAgents()
    if(isDefaultContext){
        gauntEnv.env = env
    }else{
        gauntEnv.env = [:]
    }
}

// @NonCPS
def getOnlineAgents() {
    
    def online_agents = []
    if(!isDefaultContext){
        return online_agents
    }
    def jenkins = Jenkins.instance
    for (agent in jenkins.getNodes()) {
        def computer = agent.computer
        if (computer.name == 'alpine') {
            continue
        }
        if (!computer.offline) {
            if (!gauntEnv.required_agent.isEmpty()){
                if (computer.name in gauntEnv.required_agent){
                    online_agents.add(computer.name)
                }
            }else{
                online_agents.add(computer.name)
            }
        }
    }
    logger.info("Online agents: ${online_agents}")
    return online_agents
}

/* *
 * Print list of online agents
 */
def print_agents() {
    println(gauntEnv.agents_online)
}

private def setup_agents() {
    def board_map = [:]

    // Query each agent for their connected hardware
    def jobs = [:]
    for (agent in gauntEnv.agents_online) {
        println('Agent: ' + agent)

        def agent_name = agent

        jobs[agent_name] = {
            node(agent_name) {
                stage('Query agents') {
                    // Get necessary configuration for basic work
                    if (gauntEnv.workspace == '') {
                        gauntEnv.workspace = gauntEnv.env.WORKSPACE
                        gauntEnv.build_no = gauntEnv.env.BUILD_NUMBER
                    }
                    board = nebula('update-config board-config board-name -y ' + gauntEnv.nebula_config_path + '/' +agent_name)
                    board_map[agent_name] = board
                }
            }
        }
    }

    stage('Get Available\nTest Boards') {
        parallel jobs
    }

    gauntEnv.board_map = board_map
    (agents, boards) = splitMap(board_map,true)
    println(gauntEnv.board_map)
    gauntEnv.agents = agents
    gauntEnv.boards = boards
    println(gauntEnv.agents)
    println(gauntEnv.boards)
}

private def update_agent() {
    def docker_status = gauntEnv.enable_docker
    def update_container_lib = gauntEnv.update_container_lib
    def update_requirements = gauntEnv.update_lib_requirements
    def board_map = [:]

    // Query each agent for their connected hardware
    def jobs = [:]
    for (agent in gauntEnv.agents_online) {
        println('Agent: ' + agent)

        def agent_name = agent

        jobs[agent_name] = {
            node(agent_name) {
                // clean up residue containers and detached screen sessions
                stage('Clean up residue docker containers') {
                    stepExecutor.sh 'sudo docker ps -q -f status=exited | xargs --no-run-if-empty sudo docker rm'
                    stepExecutor.sh 'sudo screen -ls | grep Detached | cut -d. -f1 | awk "{print $1}" | sudo xargs -r kill' //close all detached screen session on the agent
                    cleanWs()
                }
                // automatically update nebula config
                if(gauntEnv.update_nebula_config){
                    stage('Update Nebula Config') {
                        gauntEnv.nebula_config_path = '/tmp/'+ gauntEnv.env.JOB_NAME + '/'+ gauntEnv.env.BUILD_NUMBER
                        if(gauntEnv.nebula_config_source == 'github'){
                            dir(gauntEnv.nebula_config_path){
                                run_i('git clone -b "' + gauntEnv.nebula_config_branch + '" ' + gauntEnv.nebula_config_repo, true)
                            }
                        }else if(gauntEnv.nebula_config_source == 'netbox'){
                            gauntEnv.nebula_config_path += '/nebula-config'
                            run_i('mkdir -p ' + gauntEnv.nebula_config_path)
                            dir(gauntEnv.nebula_config_path){
                                def custom = ""
                                if(gauntEnv.netbox_include_variants == false){
                                    custom = custom + " --no-include-variants"
                                }
                                if(gauntEnv.netbox_include_children == false){
                                    custom = custom + " --no-include-children"
                                }
                                if(custom==""){
                                    custom = null
                                }
                                
                                def command_str = 'gen-config-netbox'
                                command_str += ' --netbox-ip=' + gauntEnv.netbox_ip
                                command_str += ' --netbox-port=' + gauntEnv.netbox_port
                                command_str += ' --netbox-baseurl=' + gauntEnv.netbox_base_url
                                command_str += ' --netbox-token=' + gauntEnv.netbox_token
                                command_str += (gauntEnv.netbox_test_agent == true)? "" : ' --jenkins-agent=' + agent_name
                                command_str += (gauntEnv.netbox_devices_status == null)? "" : ' --devices-status=' + gauntEnv.netbox_devices_status
                                command_str += (gauntEnv.netbox_devices_role == null)? "" : ' --devices-role=' + gauntEnv.netbox_devices_role
                                command_str += (gauntEnv.netbox_devices_tag == null)? "" : ' --devices-tag=' + gauntEnv.netbox_devices_tag
                                command_str += (gauntEnv.netbox_nebula_template == null)? "" : ' --template=' + gauntEnv.netbox_nebula_template
                                command_str += (custom == null)? "" : custom
                                command_str += ' --outfile='+ agent_name

                                nebula(command_str, true, true, false)
                            }
                        }else{
                            println(gauntEnv.nebula_config_source + ' as config source is not supported yet.')
                        }
                    }
                }
            }
        }
    }

    stage('Update Agents Tools') {
        parallel jobs
    }
}

/**
 * Add stage to agent pipeline
 * @param stage_name String name of stage
 * @return Closure of stage requested
 */
def stage_library(String stage_name) {
    stageClass = getStage(stage_name)
    return stageClass.getCls()
}

/**
 * Add stage to agent pipeline
 * @param cls Closure of stage(s). Should contain at least one stage closure.
 * @param option Defines the execution flow behavior of the stage defined in cls.
 * @param delegatedCls The stage closure that will be executed when cls fails for option 'stopWhenFail'
 */
def add_stage(cls, String option='stopWhenFail', delegatedCls=null) {
    def newCls;
    switch (option){
        case 'stopWhenFail':
            newCls = new FailSafeWrapper(cls, true, delegatedCls)
            break
        case 'continueWhenFail': 
            newCls = new FailSafeWrapper(cls, false)
            break
        case 'retryWhenFail':
            // TODO
            break
        default:
            throw new Exception('Unknown stage execution type: ' + option)
    }
    gauntEnv.stages.add(newCls)
}

private def collect_logs() {
    
    def num_boards = gauntEnv.boards.size()
    
    node('master') {
        stage('Collect Logs') {
            for (i = 0; i < num_boards; i++) {
                def agent = gauntEnv.agents[i]
                def board = gauntEnv.boards[i]
                println("Processing log for board: "+board+" ("+agent+")")
            }
        }
    }
    
}

private def log_artifacts(){
    // execute to one of available agents
    def agent = gauntEnv.agents[0]
    node(agent){
        stage('Log Artifacts'){
            def command = "telemetry grab-and-log-artifacts"
            command += " --jenkins-server ${JENKINS_URL}"
            command += " --es-server ${gauntEnv.elastic_server}"
            command += " --job-name ${gauntEnv.env.JOB_NAME} --job ${gauntEnv.env.BUILD_NUMBER}"

            // Pass Jenkins credentials if jenkins_credentials (credentials id) is set
            if (gauntEnv.credentials_id != ''){
                withCredentials([usernamePassword(credentialsId: gauntEnv.credentials_id, 
                                usernameVariable: 'JENKINS_USER',
                                passwordVariable: 'JENKINS_PASS')]) {
                    run_i(command + " --jenkins-username " + JENKINS_USER + " --jenkins-password " + JENKINS_PASS )
                }
            } else {
                run_i(command)
            }
        }
    }
}

private def run_agents() {
    // Start stages for each node with a board
    def docker_status = gauntEnv.enable_docker
    def update_container_lib = gauntEnv.update_container_lib
    def update_lib_requirements = gauntEnv.update_lib_requirements
    def jobs = [:]
    def num_boards = gauntEnv.boards.size()
    def docker_args = getDockerConfig(gauntEnv.docker_args, gauntEnv.matlab_license)
    def enable_update_boot_pre_docker = gauntEnv.enable_update_boot_pre_docker
    def pre_docker_cls = stage_library("UpdateBOOTFiles")
    docker_args.add('-v /etc/apt/apt.conf.d:/etc/apt/apt.conf.d:ro')
    docker_args.add('-v /etc/default:/default:ro')
    docker_args.add('-v /dev:/dev')
    docker_args.add('-v /etc/timezone:/etc/timezone:ro')
    
    docker_args.add('-v /etc/localtime:/etc/localtime:ro')
    if (gauntEnv.docker_host_mode) {
        docker_args.add('--network host')
    }
    if (docker_args instanceof List) {
        docker_args = docker_args.join(' ')
    }

    
    def oneNode = { agent, num_stages, stages, board, docker_stat  ->
        def k
        def ml_variants = ['rx','tx','rx_tx']
        def ml_variant_index = 0
        def lock_name = extractLockName(board, agent)

        node(agent) {
            echo "Acquiring lock for ${lock_name}"
            lock(lock_name){
                try{
                    gauntEnv.internal_stages_to_skip[board] = 0; // Initialize
                    for (k = 0; k < num_stages; k++) {
                        if (gauntEnv.internal_stages_to_skip[board] > 0) {
                            println("Skipping test stage")
                            gauntEnv.internal_stages_to_skip[board]--
                            continue;
                        }
                        println("Stage called for board: "+board)
                        println("Num arguments for stage: "+stages[k].maximumNumberOfParameters().toString()) 
                        if ((stages[k].maximumNumberOfParameters() > 1) && gauntEnv.toolbox_generated_bootbin)
                            stages[k].call(this, board, ml_variants[ml_variant_index++])
                        else
                            stages[k].call(this, board)
                    }
                }catch(NominalException ex){
                    println("oneNode: A nominal exception was encountered ${ex.getMessage()}")
                    println("Stopping execution of stages for ${board}")
                }finally {
                    println("Cleaning up after board stages");
                    cleanWs();
                }
            }
        }
    }
    
    def oneNodeDocker = { agent, num_stages, stages, board, docker_image_name, enable_update_boot_pre_docker_flag, pre_docker_closure, docker_stat, update_container, update_requirements ->
        def k
        def ml_variants = ['rx','tx','rx_tx']
        def ml_variant_index = 0
        def docker_args_agent = ''
        def lock_name = extractLockName(board, agent)

        node(agent) {
            echo "Acquiring lock for ${lock_name}"
            lock(lock_name){
                try {
                    docker_args_agent = docker_args + ' -v '+ gauntEnv.nebula_config_path + '/' + gauntEnv.env.NODE_NAME + ':/tmp/nebula:ro'
                    if (enable_update_boot_pre_docker_flag)
                        pre_docker_closure.call(this, board)
                    docker.image(docker_image_name).inside(docker_args_agent) {
                        try {
                            stage('Setup Docker') {
                                stepExecutor.sh 'apt-get clean'
                                stepExecutor.sh 'cp /tmp/nebula /etc/default/nebula'
                                stepExecutor.sh 'mkdir -p ~/.pip && cp /default/pip/pip.conf ~/.pip/pip.conf || true'
                                stepExecutor.sh 'cp /default/pyadi_test.yaml /etc/default/pyadi_test.yaml || true'
                                def deps = check_update_container_lib(update_container)
                                if (deps.size()>0){
                                    setupAgent(deps, true, update_requirements)
                                }
                                // Above cleans up so we need to move to a valid folder
                                stepExecutor.sh 'cd /tmp'
                            }
                            if (gauntEnv.check_device_status){
                                stage('Check Device Status'){
                                    def board_status = nebula("netbox.board-status --netbox-ip=" + gauntEnv.netbox_ip + " --netbox-token=" + gauntEnv.netbox_token + " --board-name=" + board)
                                    if (board_status == "Active"){
                                        comment = "Board is Active. Lock acquired and used by ${gauntEnv.env.JOB_NAME} ${gauntEnv.env.BUILD_NUMBER}"
                                        nebula("netbox.log-journal --netbox-ip=" + gauntEnv.netbox_ip + " --netbox-token=" + gauntEnv.netbox_token + " --board-name=" + board +" --kind='info' --comment='"+ comment + "'")
                                    }else{
                                        comment = "Board is not active. Skipping next stages of ${gauntEnv.env.JOB_NAME} ${gauntEnv.env.BUILD_NUMBER}"
                                        nebula("netbox.log-journal --netbox-ip=" + gauntEnv.netbox_ip + " --netbox-token=" + gauntEnv.netbox_token + " --board-name=" + board +" --kind='info' --comment='" + comment + "'")
                                        throw new NominalException('Board is not active. Skipping succeeding stages.') 
                                    }
                                }
                            }
                            gauntEnv.internal_stages_to_skip[board] = 0; // Initialize
                            for (k = 0; k < num_stages; k++) {
                                if (gauntEnv.internal_stages_to_skip[board] > 0) {
                                    println("Skipping test stage")
                                    gauntEnv.internal_stages_to_skip[board]--
                                    continue;
                                }
                                println("Stage called for board: "+board)
                                println("Num arguments for stage: "+stages[k].maximumNumberOfParameters().toString()) 
                                if ((stages[k].maximumNumberOfParameters() > 1) && gauntEnv.toolbox_generated_bootbin)
                                    stages[k].call(this, board, ml_variants[ml_variant_index++])
                                else
                                    stages[k].call(this, board)
                            }
                        }catch(NominalException ex){
                            println("oneNodeDocker: A nominal exception was encountered ${ex.getMessage()}")
                            println("Stopping execution of stages for ${board}")
                        }finally {
                            if (gauntEnv.check_device_status){
                                    comment = "Releasing lock by ${gauntEnv.env.JOB_NAME} ${gauntEnv.env.BUILD_NUMBER}"
                                    nebula("netbox.log-journal --netbox-ip=" + gauntEnv.netbox_ip + " --netbox-token=" + gauntEnv.netbox_token + " --board-name=" + board + " --kind='info' --comment='" + comment + "'")
                                }
                            println("Cleaning up after board stages");
                            cleanWs();
                        }
                    }
                }
                finally {
                    stepExecutor.sh 'docker ps -q -f status=exited | xargs --no-run-if-empty docker rm'
                }
            }
        }
    }

    for (i = 0; i < num_boards; i++) {
        def agent = gauntEnv.agents[i]
        def board = gauntEnv.boards[i]
        def stages = gauntEnv.stages
        def docker_image = gauntEnv.docker_image
        def num_stages = stages.size()

        println('Agent: ' + agent + ' Board: ' + board)
        println('Number of stages to run: ' + num_stages.toString())

/*
jobs[agent+"-"+board] = {
  node(agent) {
    for (k=0; k<num_stages; k++) {
      println("Running stage: "+k.toString());
      stages[k].call();
    }
  }
}
*/

        if (gauntEnv.enable_docker) {
            println("Enable resource queueing")
            jobs[agent + '-' + board] = {
                oneNodeDocker(
                    agent,
                    num_stages,
                    stages,
                    board,
                    docker_image,
                    enable_update_boot_pre_docker,
                    pre_docker_cls,
                    docker_status,
                    update_container_lib,
                    update_lib_requirements
                )
            };
        } else {
            jobs[agent + '-' + board] = {
                oneNode(agent, num_stages, stages, board, docker_status)
            };
        }
    }

    stage('Update and Test') {
        parallel jobs
    }
}


/* *
 * Env getter method
 */
def get_env(String param) {
    return gauntEnv[param]
}

def get_env() {
    return gauntEnv
}


/* *
 * Env setter method
 */
def set_env(String param, def value) {
    gauntEnv[param] = value
}

/* *
 * Getter method for elastic_logs fields
 */
def synchronized get_elastic_field(String board, String field, String default_value="") {
    def value = default_value
    if (gauntEnv.elastic_logs.containsKey(board)){
        if(gauntEnv.elastic_logs[board].containsKey(field)){
            value = gauntEnv.elastic_logs[board][field]
        }
    }
    return value
}

/* *
 * Setter method for elastic_logs fields
 */
def synchronized set_elastic_field(String board, String field, String value) {
    def field_map = [:]
    field_map[field] = value
    if (gauntEnv.elastic_logs.containsKey(board)){
        gauntEnv.elastic_logs[board][field] = value
    }else{
        gauntEnv.elastic_logs[board] = field_map
    }
}

/**
 * Set list of required devices for test
 * @param board_names list of strings of names of boards
 * Strings must be associated with a board configuration name.
 * For example: zynq-zc702-adv7511-ad9361-fmcomms2-3
 */
def set_required_hardware(List board_names) {
    assert board_names instanceof java.util.List
    gauntEnv.required_hardware = board_names
}

/**
 * Set list of required agent for test
 * @param agent_names list of strings of names of agent to use
 * Strings must be associated with an existing agent.
 * For example: sdg-nuc-01, master
 */
def set_required_agent(List agent_names) {
    assert agent_names instanceof java.util.List
    gauntEnv.required_agent = agent_names
    gauntEnv.agents_online = getOnlineAgents()
}

/**
 * Set URI source. Set URI source. Supported are ip or serial
 * @param iio_uri_source String of URI source
 */
def set_iio_uri_source(iio_uri_source) {
    gauntEnv.iio_uri_source = iio_uri_source
}

/**
 * Set URI serial baudrate. Set URI baudrate. Only applicable when iio_uri_source is serial
 * @param iio_uri_source Integer of URI baudrate
 */
def set_iio_uri_baudrate(iio_uri_baudrate) {
    gauntEnv.iio_uri_baudrate = iio_uri_baudrate
}

/**
 * Set elastic server address. Setting will use a non-default elastic search server
 * @param elastic_server String of server IP
 */
def set_elastic_server(elastic_server) {
    gauntEnv.elastic_server = elastic_server
}

/**
 * Set nebula debug mode. Setting true will add show-log to nebula commands
 * @param nebula_debug Boolean of debug mode
 */
def set_nebula_debug(nebula_debug) {
    gauntEnv.nebula_debug = nebula_debug
}

/**
 * Set nebula downloader local_fs source_path.
 * @param nebula_local_fs_source_root String of path
 */
def set_nebula_local_fs_source_root(nebula_local_fs_source_root) {
    gauntEnv.nebula_local_fs_source_root = nebula_local_fs_source_root
}

/**
 * Set docker args passed to docker container at runtime.
 * @param docker_args List of strings of args
 */
def set_docker_args(docker_args) {
    gauntEnv.docker_args = docker_args
}

/**
 * Enable use of docker at agent during jobs phases.
 * @param enable_docker boolean True will enable use of docker
 */
def set_enable_docker(enable_docker) {
    gauntEnv.enable_docker = enable_docker
}

/**
 * Enable use of docker host mode.
 * @param docker_host_mode boolean True will enable use of docker host mode
 */
def set_docker_host_mode(docker_host_mode) {
    gauntEnv.docker_host_mode = docker_host_mode
}

/**
 * Enable update boot to be run before docker is launched.
 * @param set_enable_update_boot_pre_docker boolean True will run update boot stage before docker is launch
 */
def set_enable_update_boot_pre_docker(enable_update_boot_pre_docker) {
    gauntEnv.enable_update_boot_pre_docker = enable_update_boot_pre_docker
}

/**
 * Enable sending of elastic telemetry
 * @param send_results boolean True will run enable sending of telemetry to elastic server
 */
def set_send_telemetry(send_results) {
    gauntEnv.send_results = send_results
}

/**
 * Set the max_retry variable of gauntEnv used in retrying some sh/bat steps.
 * @param max_retry integer replaces default gauntEnv.max_retry
 */
def set_max_retry(max_retry) {
    gauntEnv.max_retry = max_retry
}

/**
 * Set the job_trigger variable of gauntEnv used in identifying what triggered the execution of the pipeline
 * @param trigger string replaces default gauntEnv.job_trigger
 * set to manual(default) if manually triggert or auto:<jenkins project name>:<jenkins build number> for auto triggered builds
 */
def set_job_trigger(trigger) {
    gauntEnv.job_trigger = trigger
}

/**
 * Set the credentials_id variable of gauntEnv used in downloading artifacts in Log Artifacts stage
 * @param credentials_id is a username-password credentials stored in Jenkins with read access
 * set to '' by default 
 */
def set_credentials_id(credentials_id) {
    gauntEnv.credentials_id = credentials_id
}

/**
 * Set list of MATLAB commands
 * @param matlab_commands list of strings of commands to be executed in MATLAB
 * For example: "runHWTests('AD9361')"
 */
def set_matlab_commands(List matlab_commands) {
    assert matlab_commands instanceof java.util.List
    gauntEnv.matlab_commands = matlab_commands
}

/**
 * Set timeout for MATLAB process
 * @param matlab_timeout string in format <value><unit> for running MATLAB executable
 * For example: "10m" (default)
 */
def set_matlab_timeout(matlab_timeout) {
    gauntEnv.matlab_timeout = matlab_timeout
}

/**
 * Set type of MATLAB license file
 * @param matlab_license acceptable values are 'network' for 'machine'
 * 'network' for network license and 'machine' for machine-specific license
 */
def set_matlab_license(matlab_license) {
    gauntEnv.matlab_license = matlab_license
}

/**
 * Enables updating of nebula-config used by nebula
 * @param enable boolean replaces default gauntEnv.update_nebula_config
 * set to true(default) to update nebula_config of agent, or set to false otherwise
 */
def set_update_nebula_config(boolean enable) {
    gauntEnv.update_nebula_config = enable
}

/**
 * Check if project is part of a multibranch pipeline using 'checkout scm'
 * Declaring the GitHub Project url in a non-multibranch pipeline does not conflict with checking.
 */
def isMultiBranchPipeline(repo_url) {
    isMultiBranch = false
    branch = ""
    ref = ""
    println("Checking if multibranch pipeline..") 
    if (gauntEnv.env.BRANCH_NAME){
        println("Pipeline is multibranch.")
        //check if the multibranch pipeline is for this repo
        def actualRepoUrl = scm.userRemoteConfigs[0].url
        if (actualRepoUrl == repo_url){
            branch = gauntEnv.env.BRANCH_NAME
            if (branch.startsWith("PR-")) {
                pr_number = branch.substring(3)
                println "Branch is a pull request (PR number: ${pr_number})"
                ref = "+refs/pull/${pr_number}/head:refs/remotes/origin/PR-${pr_number}"
            } else {
                println "Branch is not a pull request."
                ref = "+refs/heads/${branch}:refs/remotes/origin/${branch}"
            }
            isMultiBranch = true  
        }else{
            //repo is cloned only in another multibranch pipeline
            println("Pipeline is not CI for current repo.")
        }
    }else {
        println("Pipeline is not multibranch.")
    }
    return [isMultiBranch: isMultiBranch, branch: branch, ref: ref]
}

/**
 * Set the value of reference branch for the board recovery stage.
 * @param reference string. Available options: 'SD', 'boot_partition_master', 'boot_partition_release'
 */
def set_recovery_reference(reference) {
    gauntEnv.recovery_ref = reference
}

/**
 * Enables logging of test build artifacts to telemetry at the end of the build
 * @param enable boolean replaces default gauntEnv.log_artifacts
 * set to true to log artifacts data to telemetry, or set to false(default) otherwise
 */
def set_log_artifacts(boolean enable) {
    gauntEnv.log_artifacts = enable
}

/**
 * Main method for starting pipeline once configuration is complete
 * Once called all agents are queried for attached boards and parallel stages
 * will generated and mapped to relevant agents
 */
def run_stages() {
    // make sure log collection stage is called for the whole build
    // regardless of status i.e SUCCESS, UNSTABLE, FAILURE
    catchError {
        setup_agents()
        check_required_hardware()
        run_agents()
    }
    // collect_logs()
    if (gauntEnv.log_artifacts){
        log_artifacts()
    }
}

def update_agents() {
    update_agent()
}

private def check_required_hardware() {

    stage('Check Required Hardware'){
        def rh = gauntEnv.required_hardware
        def ab = gauntEnv.boards
        def s = rh.size()
        def b = ab.size()
        def filtered_board_list = []
        def filtered_agent_list = []
        def found_rh = []
        def special_naming_cases = [
            "zynqmp-adrv9009-zu11eg-revb-adrv2crr-fmc-revb":\
            "zynqmp-adrv9009-zu11eg-revb-adrv2crr-fmc-revb-jesd204-fsm",
            "zynqmp-adrv9009-zu11eg-revb-adrv2crr-fmc-revb-vsync-fmcomms8":\
            "zynqmp-adrv9009-zu11eg-revb-adrv2crr-fmc-revb-sync-fmcomms8-jesd204-fsm"
        ]

        if (s != 0){
            // if required_hardware is not set, required hardware will be taken from nebula-config
            // recreate required_hardware list
            println("Found boards:")
            for (k = 0; k < b; k++) {
                def board = gauntEnv.boards[k]
                def agent = gauntEnv.agents[k]
                if (rh.contains(board)){
                    println("Found required hardware Board: "+ board + " Agent: "+ agent)
                    filtered_board_list.add(board)
                    filtered_agent_list.add(agent)
                    found_rh.add(board)
                }else{
                    def base = board
                    def variant = null
                    // get base name from variant
                    if(board.contains("-v")){
                        base = board.split("-v")[0]
                        variant = board.split("-v")[1]
                    }
                    // get base name from special cases, this takes precedence
                    if(special_naming_cases.containsKey(board)){
                        base = special_naming_cases[board]
                    }
                    if(rh.contains(base)){
                        println("Found required hardware Board: "+ base + " Variant"+ variant +" Agent: "+ agent)
                        filtered_board_list.add(board)
                        filtered_agent_list.add(agent)   
                        if(!found_rh.contains(base)){
                            found_rh.add(base)
                        }
                    }
                }
            }

            for (k=0;k < s;k++){
                required_board = rh[k]
                if(found_rh.contains(required_board)){
                    rh.remove(required_board)
                }
            }

            gauntEnv.boards = filtered_board_list
            gauntEnv.agents = filtered_agent_list

            if(rh.size() > 0){
                println("Some required hardwares cannot be found :" + rh.toString())
                currentBuild.result = "UNSTABLE"
            }
        }else{
            println("required_hardware not set, will skip check.")
        }
    }
}

@NonCPS
private def splitMap(map, do_split=false) {
    def keys = []
    def values = []
    def tmp;
    for (entry in map) {
        if (do_split)
        {
            tmp = entry.value
            tmp = tmp.split(",")

            for (i=0;i<tmp.size();i++)
            {
                keys.add(entry.key)
                values.add(tmp[i].replaceAll(" ",""))
            }
        }
        else
        {
            keys.add(entry.key)
            values.add(entry.value)
        }
    }
    return [keys, values]
}

private def checkOs() {
    if (stepExecutor.isUnix()) {
        def uname = stepExecutor.sh(script: 'uname', returnStdout: true)
        if (uname.startsWith('Darwin')) {
            return 'Macos'
        }
        // Optionally add 'else if' for other Unix OS
        else {
            return 'Linux'
        }
    }
    else {
        return 'Windows'
    }
}

def nebula(cmd, full=false, show_log=false, report_error=false) {
    // full=false
    def script_out = ''
    if (gauntEnv.nebula_debug) {
        show_log = true
    }
    if (show_log) {
        cmd = 'show-log ' + cmd
    }
    cmd = 'nebula ' + cmd
    if (checkOs() == 'Windows') {
        script_out = stepExecutor.bat(script: cmd, returnStdout: true).trim()
    }
    else {
        if (report_error){
            def outfile = 'out.out'
            def nebula_traceback = []
            cmd = cmd + " 2>&1 | tee ${outfile}"
            cmd = 'set -o pipefail; ' + cmd 
            try{
                stepExecutor.sh cmd
                if (stepExecutor.fileExists(outfile))
                    script_out = stepExecutor.readFile(outfile).trim()
            }catch(Exception ex){
                if (stepExecutor.fileExists(outfile)){
                    script_out = stepExecutor.readFile(outfile).trim()
                    lines = script_out.split('\n')
                    def err_line = false
                    for (i = 1; i < lines.size(); i++) {
                        if (lines[i].matches('Traceback .+')) {
                            err_line = true
                        }
                        if(err_line){
                            if (!lines[i].matches('.*nebula.{1}uart.*')){
                                nebula_traceback << lines[i]
                            }
                        }
                    }
                }
                if (nebula_traceback.size() > 0){
                    throw new Exception(nebula_traceback.join("\n"))
                }
                throw new Exception("nebula failed")
            }
        }else{
            script_out = stepExecutor.sh(script: cmd, returnStdout: true)
            if (script_out == null){
                script_out = ""
            }
            script_out = script_out.trim()
        }
    }
    // Remove lines
    if (!full) {
        lines = script_out.split('\n')
        if (lines.size() == 1) {
            return script_out
        }
        out = ''
        added = 0
        for (i = 1; i < lines.size(); i++) {
            if (lines[i].contains('WARNING')) {
                continue
            }
            if (!lines[i].matches(/.*[A-Za-z0-9]+.*/)) {
                continue
            }
            if (added > 0) {
                out = out + '\n'
            }
            out = out + lines[i]
            added = added + 1
        }
        return out
    }
    return script_out
}

def sendLogsToElastic(... args) {
    full = false
    cmd = args.join(' ')
    if (gauntEnv.elastic_server) {
        cmd = ' --server=' + gauntEnv.elastic_server + ' ' + cmd
    }
    cmd = 'telemetry log-boot-logs ' + cmd
    println(cmd)
    if (checkOs() == 'Windows') {
        script_out = stepExecutor.bat(script: cmd, returnStdout: true)?.trim()
    }
    else {
        script_out = stepExecutor.sh(script: cmd, returnStdout: true)?.trim()
    }
    // Remove lines
    out = ''
    if (!full) {
        lines = script_out?.split('\n')
        if (lines?.size() == 1) {
            return script_out
        }
        out = ''
        added = 0
        for (i = 1; i < lines?.size(); i++) {
            if (lines[i].contains('WARNING')) {
                continue
            }
            if (added > 0) {
                out = out + '\n'
            }
            out = out + lines[i]
            added = added + 1
        }
    }
    return out
}

def String getURIFromSerial(String board){
    // Utility method to get uri from IIO device serial number
    if (board == 'm2k') {
        serial_no = nebula('update-config board-config serial --board-name='+board)
    }
    else {
        serial_no = nebula('update-config board-config instr-serial --board-name='+board)
    }
    cmd="iio_info -s | grep serial="+serial_no+" | grep -Po \"\\[.*:.*\" | sed 's/.\$//' | cut -c 2-"
    instr_uri = stepExecutor.sh(script:cmd, returnStdout: true).trim()
    return instr_uri
}

private def install_nebula(update_requirements=false) {
    if (checkOs() == 'Windows') {
        run_i('git clone -b '+  gauntEnv.nebula_branch + ' ' + gauntEnv.nebula_repo, true)
        dir('nebula')
        {
            if (update_requirements){
                run_i('pip install -r requirements.txt', true)
            }
            run_i('python setup.py install', true)
        }
    }
    else {
        def scmVars = checkout([
            $class : 'GitSCM',
            branches : [[name: "*/${gauntEnv.nebula_branch}"]],
            doGenerateSubmoduleConfigurations: false,
            extensions: [[$class: 'LocalBranch', localBranch: "**"]],
            submoduleCfg: [],
            userRemoteConfigs: [[credentialsId: '', url: "${gauntEnv.nebula_repo}"]]
        ])
        stepExecutor.sh 'pip3 uninstall nebula -y || true'
        stepExecutor.sh 'pip3 install .'
    }
}

private def install_libiio() {
    if (checkOs() == 'Windows') {
        run_i('git clone -b ' + gauntEnv.libiio_branch + ' ' + gauntEnv.libiio_repo, true)
        dir('libiio')
        {
            stepExecutor.bat 'mkdir build'
            dir('build')
            {
                stepExecutor.bat 'cmake .. -DPYTHON_BINDINGS=ON -DWITH_SERIAL_BACKEND=ON -DHAVE_DNS_SD=OFF'
                stepExecutor.bat 'cmake --build . --config Release --install'
            }
        }
    }
    else {
        def scmVars = checkout([
            $class : 'GitSCM',
            branches : [[name: "refs/tags/${gauntEnv.libiio_branch}"]],
            doGenerateSubmoduleConfigurations: false,
            extensions: [[$class: 'LocalBranch', localBranch: "**"]],
            submoduleCfg: [],
            userRemoteConfigs: [[credentialsId: '', url: "${gauntEnv.libiio_repo}"]]
        ])
        stepExecutor.sh 'mkdir -p build'
        dir('build')
        {
            stepExecutor.sh 'cmake .. -DPYTHON_BINDINGS=ON -DWITH_SERIAL_BACKEND=ON -DHAVE_DNS_SD=OFF'
            stepExecutor.sh 'make'
            stepExecutor.sh 'sudo make install'
            stepExecutor.sh 'ldconfig'
            // install python bindings
            dir('bindings/python'){
                stepExecutor.sh 'python3 setup.py install'
            }
        }
    }
}

private def install_telemetry(update_requirements=false){
    if (checkOs() == 'Windows') {
        run_i('git clone -b ' + gauntEnv.telemetry_branch + ' ' + gauntEnv.telemetry_repo, true)
        dir('telemetry')
        {
            if (update_requirements){
                run_i('pip install -r requirements.txt', true)
            }
            run_i('python setup.py install', true)
        }
    }else{
        // stepExecutor.sh 'pip3 uninstall telemetry -y || true'
        def scmVars = checkout([
            $class : 'GitSCM',
            branches : [[name: "*/${gauntEnv.telemetry_branch}"]],
            doGenerateSubmoduleConfigurations: false,
            extensions: [[$class: 'LocalBranch', localBranch: "**"]],
            submoduleCfg: [],
            userRemoteConfigs: [[credentialsId: '', url: "${gauntEnv.telemetry_repo}"]]
        ])
        if (update_requirements){
            run_i('pip3 install -r requirements.txt', true)
        }
        stepExecutor.sh 'pip3 install .'
    }
}

private def check_update_container_lib(update_container_lib=false) {
    def deps = []
    def default_branches = ['main', 'master']
    if (update_container_lib){
        deps = gauntEnv.required_libraries
    }else{
        for(lib in gauntEnv.required_libraries){
            if(!default_branches.contains(gauntEnv[lib+'_branch'])){
                deps.add(lib)
            }
        }
    }
    return deps
}

private def setupAgent(deps, skip_cleanup = false, update_requirements=false) {
    try {
        def i;
        for (i = 0; i < deps.size; i++) {
            println(deps[i])
            if (deps[i] == 'nebula') {
                install_nebula(update_requirements)
            }
            if (deps[i] == 'libiio') {
                install_libiio()
            }
            if (deps[i] == 'telemetry') {
                install_telemetry(update_requirements)
            }
         }
    }
    finally {
        if (!skip_cleanup)
            cleanWs()
    }
}

def get_gitsha(String board){

    hdl_hash = "NA"
    linux_hash = "NA"
    linux_git_sha = "NA"
    linux_folder = "NA"

    if (gauntEnv.nebula_local_fs_source_root == "local_fs"){
        set_elastic_field(board, 'hdl_hash', hdl_hash)
        set_elastic_field(board, 'linux_hash', linux_hash)
        return
    }

    if (gauntEnv.firmware_boards.contains(board)){
        set_elastic_field(board, 'hdl_hash', hdl_hash)
        set_elastic_field(board, 'linux_hash', linux_hash)
        return
    }
    
    if (stepExecutor.fileExists('outs/properties.yaml')){
        dir ('outs'){
            script{ properties = readYaml file: 'properties.yaml' }
        }
        if (gauntEnv.bootPartitionBranch == 'NA'){
            hdl_hash = properties.hdl_git_sha + " (" + properties.hdl_folder + ")"
            linux_hash = properties.linux_git_sha + " (" + properties.linux_folder + ")" 
        }else{
            hdl_hash = properties.hdl_git_sha + " (" + properties.bootpartition_folder + ")"
            linux_hash = properties.linux_git_sha + " (" + properties.bootpartition_folder + ")"
        }
    } else if(stepExecutor.fileExists('outs/properties.txt')){
        dir ('outs'){
            def file = stepExecutor.readFile 'properties.txt'
            lines = file.readLines()
            for (line in lines){
                echo line
                if (line.contains("git_sha=")){
                    echo "git_sha found"
                    linux_git_sha = line.replace("git_sha=","")
                }
                if (line.contains("git_sha_date=")){
                    echo "git_sha_date found"
                    linux_folder = line.replace("git_sha_date=","")
                }
            }
        }
        linux_hash = linux_git_sha + " (" + linux_folder + ")"
        hdl_hash = "NA"
    } else {
        return
    }

    echo "Hashes set hdl: ${hdl_hash}, linux: ${linux_hash}"
    set_elastic_field(board, 'hdl_hash', hdl_hash)
    set_elastic_field(board, 'linux_hash', linux_hash)
}

private def check_for_marker(String board){
    def marker = ''
    def board_name = board
    def valid_markers = [ "cmos", "lvds"]
    def noos_markers = ["iio_example", "dummy_example", "iio", "demo", "dma_example", "dma_irq_example" ]
    if (board.contains("-v")){
        if (board.split("-v")[1] in valid_markers){
            board_name = board.split("-v")[0]
            marker = ' --' + board.split("-v")[1]
            return [board_name:board_name, marker:marker]
        }else if(board.split("-v")[1].replace("-","_") in noos_markers){
            board_name = board.split("-v")[0]
            return [board_name:board_name, marker:marker]
        }else {
            board_name = board.replace("-v","-")
            return [board_name:board_name, marker:marker]
        }
    }
    else {
        return [board_name:board_name, marker:marker]
    }
}

private def extractLockName(String bname, String agent){
    echo "Extracting resource lockname from ${bname}"
    def lockName = bname
    if (bname.contains("-v")){
        lockName = bname.split("-v")[0]
    }
    for (cat in gauntEnv.board_sub_categories){
        if(lockName.contains('-' + cat))
            lockName = lockName.replace('-' + cat, "")
    }
    // support carrier with multiple daughter boards, e.g RPi PMOD Hats
    // use serial-id (if exists) as unique carrier identifier that will be used as lock name.
    node(agent){
        try{
            def serial_str = nebula("update-config board-config serial -b ${bname}")
            if (serial_str){
                lockName = serial_str
            }
        }catch(Exception ex){
            echo getStackTrace(ex)
            println("serial-id is not defined. Will use other reference as lockname")
        }
    }
    return lockName
}

def run_i(cmd, do_retry=false) {
    def retry_count = 1
    if(do_retry){
        retry_count = gauntEnv.max_retry
    }
    stepExecutor.retry(retry_count){
        if (checkOs() == 'Windows') {
            stepExecutor.bat cmd
        }
        else {
            stepExecutor.sh cmd
        }
    }
}

private def String getStackTrace(Throwable aThrowable){
    // Utility method to print the stack trace of an error
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    PrintStream ps = new PrintStream(baos, true);
    aThrowable.printStackTrace(ps);
    return baos.toString();
}

private def  createMFile(){
    // Utility method to write matlab commands in a .m file
    def String command_oneline = gauntEnv.matlab_commands.join(";")
    stepExecutor.writeFile file: 'matlab_commands.m', text: command_oneline
    stepExecutor.sh 'ls -l matlab_commands.m'
    stepExecutor.sh 'cat matlab_commands.m'
}

private def parseForLogging (String stage, String xmlFile, String board) {
    stage_logs = stage + '_logs'
    forLogging = [:]
    forLogging.put(stage_logs,['errors', 'failures', 'skipped', 'tests'])
    println forLogging.keySet()
    forLogging."${stage_logs}".each {
        cmd = 'cat ' + xmlFile + ' | sed -rn \'s/.*' 
        cmd+= it + '="([0-9]+)".*/\\1/p\''
        set_elastic_field(board.replaceAll('_', '-'), stage + '_' + it, stepExecutor.sh(returnStdout: true, script: cmd).trim())
    }
}
