package sdg
import sdg.FailSafeWrapper
import sdg.NominalException
/** A map that holds all constants and data members that can be override when constructing  */
gauntEnv

/**
 * Imitates a constructor
 * Defines an instance of Consul object. All according to api
 * @param dependencies - List of strings which are names of dependencies
 * @param hdlBranch - String of name of hdl branch to use for bootfile source
 * @param linuxBranch - String of name of linux branch to use for bootfile source
 * @param bootPartitionBranch - String of name of boot partition branch to use for bootfile source, set to 'NA' if hdl and linux is to be used
 * @param firmwareVersion - String of name of firmware version branch to use for pluto and m2k
 * @param bootfile_source - String location of bootfiles. Options: sftp, artifactory, http, local
 * @return constructed object
 */
def construct(List dependencies, hdlBranch, linuxBranch, bootPartitionBranch, firmwareVersion, bootfile_source) {
    gauntEnv = [
            dependencies: dependencies,
            hdlBranch: hdlBranch,
            linuxBranch: linuxBranch,
            bootPartitionBranch: bootPartitionBranch,
            branches: ( bootPartitionBranch == 'NA')? [linuxBranch, hdlBranch]: ['boot_partition', bootPartitionBranch],
            firmwareVersion: firmwareVersion,
            bootfile_source: bootfile_source,
            agents_online: '',
            debug: false,
            board_map: [:],
            stages: [],
            agents: [],
            boards: [],
            required_hardware: [],
            enable_docker: false,
            docker_image: 'tfcollins/sw-ci:latest',
            docker_args: ['MATLAB','Vivado'],
            enable_update_boot_pre_docker: false,
            setup_called: false,
            nebula_debug: false,
            nebula_local_fs_source_root: '/var/lib/tftpboot',
            elastic_server: '',
            configure_called: false,
            pytest_libiio_repo: 'https://github.com/tfcollins/pytest-libiio.git',
            pytest_libiio_branch: 'master',
            pyadi_iio_repo: 'https://github.com/analogdevicesinc/pyadi-iio.git',
            pyadi_iio_branch: 'master',
            libad9361_iio_repo: 'https://github.com/analogdevicesinc/libad9361-iio.git',
            libad9361_iio_branch : 'master',
            nebula_repo: 'https://github.com/tfcollins/nebula.git',
            nebula_branch: 'master',
            libiio_repo: 'https://github.com/analogdevicesinc/libiio.git',
            libiio_branch: 'master',
            telemetry_repo: 'https://github.com/tfcollins/telemetry.git',
            telemetry_branch: 'master'
    ]

    gauntEnv.agents_online = getOnlineAgents()
}

/* *
 * Print list of online agents
 */
def print_agents() {
    println(gauntEnv.agents_online)
}

/* *
 * Env getter method
 */
def get_env(String param) {
    return gauntEnv[param]
}

/* *
 * Env setter method
 */
def set_env(String param, String value) {
    gauntEnv[param] = value
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
                    board = nebula('update-config board-config board-name')
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
    gauntEnv.agents = agents
    gauntEnv.boards = boards
}

private def update_agent() {
    def docker_status = gauntEnv.enable_docker
    def board_map = [:]

    // Query each agent for their connected hardware
    def jobs = [:]
    for (agent in gauntEnv.agents_online) {
        println('Agent: ' + agent)

        def agent_name = agent

        jobs[agent_name] = {
            node(agent_name) {
                stage('Update agents') {
                    sh 'mkdir -p /usr/app'
                    sh 'rm -rf /usr/app/*'
                    setupAgent(['nebula','libiio', 'telemetry'], false, docker_status)
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
    switch (stage_name) {
    case 'UpdateBOOTFiles':
            println('Added Stage UpdateBOOTFiles')
            cls = { String board ->
                try {
                stage('Update BOOT Files') {
                    println("Board name passed: "+board)
                    println(gauntEnv.branches.toString())
                    if (board=="pluto")
                        nebula('dl.bootfiles --board-name=' + board + ' --branch=' + gauntEnv.firmwareVersion)
                    else
                        nebula('dl.bootfiles --board-name=' + board + ' --source-root="' + gauntEnv.nebula_local_fs_source_root + '" --source=' + gauntEnv.bootfile_source
                                +  ' --branch="' + gauntEnv.branches.toString() + '"')
                    nebula('manager.update-boot-files --board-name=' + board + ' --folder=outs', full=false, show_log=true)
                    if (board=="pluto")
                        nebula('uart.set-local-nic-ip-from-usbdev --board-name=' + board)
                }}
                catch(Exception ex) {
                    cleanWs();
                    throw new Exception('Update boot files failed');
                }
      };
            break
    case 'CollectLogs':
            println('Added Stage CollectLogs')
            cls = {
                stage('Collect Logs') {
                    echo 'Collect Logs'
                }
      };
            break
    case 'SendResults':
            println('Added Stage SendResults')
            cls = { String board ->
                stage('SendLogsToElastic') {
                    echo 'Starting send log to elastic search'
                    cmd = 'boot_folder_name ' + board
                    cmd += ' hdl_hash NA'
                    cmd += ' linux_hash NA'
                    cmd += ' hdl_branch NA'
                    cmd += ' linux_branch NA'
                    cmd += ' is_hdl_release False'
                    cmd += ' is_linux_release False'
                    cmd += ' uboot_reached False'
                    cmd += ' linux_prompt_reached False'
                    cmd += ' drivers_enumerated False'
                    cmd += ' dmesg_warnings_found False'
                    cmd += ' dmesg_errors_found False'
                    // cmd +="jenkins_job_date datetime.datetime.now(),
                    cmd += ' jenkins_build_number ' + env.BUILD_NUMBER
                    cmd += ' jenkins_project_name ' + env.JOB_NAME
                    cmd += ' jenkins_agent ' + env.NODE_NAME
                    sendLogsToElastic(cmd)
                }
      };
            break
    case 'LinuxTests':
            println('Added Stage LinuxTests')
            cls = { String board ->
                stage('Linux Tests') {
                    def failed_test = ''
                    try {
                        run_i('pip3 install pylibiio')
                        //def ip = nebula('uart.get-ip')
                        def ip = nebula('update-config network-config dutip --board-name='+board)
                        try{
                            nebula("net.check-dmesg --ip='"+ip+"' --board-name="+board)
                        }catch(Exception ex) {
                            failed_test = failed_test + "[dmesg check failed: $ex]"
                        }

                        try{
                            nebula('driver.check-iio-devices --uri="ip:'+ip+'" --board-name='+board)
                        }catch(Exception ex) {
                            failed_test = failed_test + " [iio_devices check failed: $ex]"
                        }
                        if(failed_test && !failed_test.allWhitespace){
                            throw new Exception("failed_test")
                        }
                    }catch(Exception ex) {
                        throw new NominalException("Linux Test Failed: $ex")
                    }finally{
                        // Rename logs
                        run_i("if [ -f dmesg.log ]; then mv dmesg.log dmesg_" + board + ".log; fi")
                        run_i("if [ -f dmesg_err.log ]; then mv dmesg_err.log dmesg_" + board + "_err.log; fi")
                        run_i("if [ -f dmesg_warn.log ]; then mv dmesg_warn.log dmesg_" + board + "_warn.log; fi")
                        archiveArtifacts artifacts: '*.log', followSymlinks: false, allowEmptyArchive: true
                    }
                }
            };
            break
    case 'PyADITests':
            cls = { String board ->
                stage('Run Python Tests') {
                    try
                    {
                        //def ip = nebula('uart.get-ip')
                        def ip = nebula('update-config network-config dutip --board-name='+board)
                        println('IP: ' + ip)
                        // temporarily get pytest-libiio from another source
                        sh 'git clone -b "' + gauntEnv.pytest_libiio_branch + '" ' + gauntEnv.pytest_libiio_repo
                        dir('pytest-libiio'){
                            run_i('python3 setup.py install')
                        }
                        sh 'git clone -b "' + gauntEnv.pyadi_iio_branch + '" ' + gauntEnv.pyadi_iio_repo
                        dir('pyadi-iio')
                        {
                            run_i('pip3 install -r requirements.txt')
                            run_i('pip3 install -r requirements_dev.txt')
                            run_i('pip3 install pylibiio')
                            run_i('mkdir testxml')
                            board = board.replaceAll('-', '_')
                            cmd = "python3 -m pytest --junitxml=testxml/" + board + "_reports.xml --adi-hw-map -v -k 'not stress' -s --uri='ip:"+ip+"' -m " + board
                            def statusCode = sh script:cmd, returnStatus:true
                            if ((statusCode != 5) && (statusCode != 0)){
                                // Ignore error 5 which means no tests were run
                                throw new NominalException('PyADITests Failed')
                            }                
                        }
                    }
                    finally
                    {
                        junit testResults: 'pyadi-iio/testxml/*.xml', allowEmptyResults: true                    
                    }
                }
            }
            break
    case 'LibAD9361Tests':
            cls = { String board ->
                def supported_boards = ['zynq-zed-adv7511-ad9361-fmcomms2-3',
                                        'zynq-zc706-adv7511-ad9361-fmcomms5',
                                        'zynq-adrv9361-z7035-fmc',
                                        'zynq-zed-adv7511-ad9364-fmcomms4',
                                        'pluto']
                if(supported_boards.contains(board) && gauntEnv.libad9361_iio_branch != null){
                    try{
                        stage("Test libad9361") {
                            def ip = nebula("update-config -s network-config -f dutip --board-name="+board)
                            sh 'git clone -b '+ gauntEnv.libad9361_iio_branch + ' ' + gauntEnv.libad9361_iio_repo
                            dir('libad9361-iio')
                            {
                                sh 'mkdir build'
                                dir('build')
                                {
                                    sh 'cmake ..'
                                    sh 'make'
                                    sh 'URI_AD9361="ip:'+ip+'" ctest -T test --no-compress-output -V'
                                }
                            }
                        }
                    }
                    finally
                    {
                        dir('libad9361-iio/build'){
                            xunit([CTest(deleteOutputFiles: true, failIfNotNew: true, pattern: 'Testing/**/*.xml', skipNoTestFiles: false, stopProcessingIfError: true)])
                        }
                    }
                }else{
                    println("LibAD9361Tests: Skipping board: "+board)
                }
            }
            break
    default:
        throw new Exception('Unknown library stage: ' + stage_name)
    }

    return cls
}

/**
 * Add stage to agent pipeline
 * @param cls Closure of stage(s). Should contain at least one stage closure.
 */
def add_stage(cls, String option='stopWhenFail') {
    def newCls;
    switch (option){
        case 'stopWhenFail':
            newCls = new FailSafeWrapper(cls, true)
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

private def run_agents() {
    // Start stages for each node with a board
    def docker_status = gauntEnv.enable_docker
    def jobs = [:]
    def num_boards = gauntEnv.boards.size()
    def docker_args = getDockerConfig(gauntEnv.docker_args)
    def enable_update_boot_pre_docker = gauntEnv.enable_update_boot_pre_docker
    def pre_docker_cls = stage_library("UpdateBOOTFiles")
    docker_args.add('-v /etc/default:/default:ro')
    docker_args.add('-v /dev:/dev')
    docker_args.add('-v /usr/app:/app')
    docker_args.add('--network host')
    if (docker_args instanceof List) {
        docker_args = docker_args.join(' ')
    }

    
    def oneNode = { agent, num_stages, stages, board, docker_stat  ->
        def k
        node(agent) {
            for (k = 0; k < num_stages; k++) {
                println("Stage called for board: "+board)
                stages[k].call(board)
            }
            cleanWs();
        }
    }
    
    def oneNodeDocker = { agent, num_stages, stages, board, docker_image_name, enable_update_boot_pre_docker_flag, pre_docker_closure, docker_stat ->
        def k
        node(agent) {
            try {
                if (enable_update_boot_pre_docker_flag)
                    pre_docker_closure.call(board)
                docker.image(docker_image_name).inside(docker_args) {
                    try {
                        stage('Setup Docker') {
                            sh 'cp /default/nebula /etc/default/nebula'
                            sh 'cp /default/pip.conf /etc/pip.conf || true'
                            sh 'cp /default/pydistutils.cfg /root/.pydistutils.cfg || true'
                            sh 'mkdir -p /root/.config/pip && cp /default/pip.conf /root/.config/pip/pip.conf || true'
                            sh 'cp /default/pyadi_test.yaml /etc/default/pyadi_test.yaml || true'
                            sh 'cp -r /app/* "${PWD}"/'
                            setupAgent(['libiio','nebula','telemetry'], true, docker_status);
                            // Above cleans up so we need to move to a valid folder
                            sh 'cd /tmp'
                        }
                        for (k = 0; k < num_stages; k++) {
                            println("Stage called for board: "+board)
                            stages[k].call(board)
                        }
                    }
                    finally {
                        println("Cleaning up after board stages");
                        cleanWs();
                    }
                }
            }
            finally {
                sh 'docker ps -q -f status=exited | xargs --no-run-if-empty docker rm'
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
            jobs[agent + '-' + board] = { oneNodeDocker(agent, num_stages, stages, board, docker_image, enable_update_boot_pre_docker, pre_docker_cls, docker_status) };
        } else{
            jobs[agent + '-' + board] = { oneNode(agent, num_stages, stages, board, docker_status) };
        }
    }

    stage('Update and Test') {
        parallel jobs
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
 * Enable update boot to be run before docker is launched.
 * @param set_enable_update_boot_pre_docker boolean True will run update boot stage before docker is launch
 */
def set_enable_update_boot_pre_docker(enable_update_boot_pre_docker) {
    gauntEnv.enable_update_boot_pre_docker = enable_update_boot_pre_docker
}

private def check_required_hardware() {
    def s = gauntEnv.required_hardware.size()
    def b = gauntEnv.boards.size()
    def filtered_board_list = []
    def filtered_agent_list = []

    println("Found boards:")
    for (k = 0; k < b; k++) {
        println("Agent: "+gauntEnv.agents[k]+" Board: "+gauntEnv.boards[k])
    }
    for (i = 0; i < s; i++) {
        if (! gauntEnv.boards.contains(gauntEnv.required_hardware[i]) ) {
            error(gauntEnv.required_hardware[i] + ' not found in harness. Failing pipeline')
        }
        // Filter out
        def indx = gauntEnv.boards.indexOf(gauntEnv.required_hardware[i])
        filtered_board_list.add(gauntEnv.boards[indx])
        filtered_agent_list.add(gauntEnv.agents[indx])
    }
    // Update to filtered lists
    if (s > 0) {
        gauntEnv.boards = filtered_board_list
        gauntEnv.agents = filtered_agent_list
    }
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
    collect_logs()
}

def update_agents() {
    update_agent()
}

// Private methods
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

@NonCPS
private def getOnlineAgents() {
    def jenkins = Jenkins.instance
    def online_agents = []
    for (agent in jenkins.getNodes()) {
        def computer = agent.computer
        if (computer.name == 'alpine') {
            continue
        }
        if (!computer.offline) {
            online_agents.add(computer.name)
        }
    }
    println(online_agents)
    return online_agents
}

private def checkOs() {
    if (isUnix()) {
        def uname = sh script: 'uname', returnStdout: true
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

def nebula(cmd, full=false, show_log=false) {
    // full=false
    if (gauntEnv.nebula_debug) {
        show_log = true
    }
    if (show_log) {
        cmd = 'show-log ' + cmd
    }
    cmd = 'nebula ' + cmd
    if (checkOs() == 'Windows') {
        script_out = bat(script: cmd, returnStdout: true).trim()
    }
    else {
        script_out = sh(script: cmd, returnStdout: true).trim()
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
    }
    return out
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
        script_out = bat(script: cmd, returnStdout: true).trim()
    }
    else {
        script_out = sh(script: cmd, returnStdout: true).trim()
    }
    // Remove lines
    out = ''
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
            if (added > 0) {
                out = out + '\n'
            }
            out = out + lines[i]
            added = added + 1
        }
    }
    return out
}

private def clone_nebula() {
    if (checkOs() == 'Windows') {
        bat 'git clone -b '+  gauntEnv.nebula_branch + ' ' + gauntEnv.nebula_repo
    }
    else {
        sh 'pip3 uninstall nebula -y || true'
        sh 'git clone -b ' + gauntEnv.nebula_branch + ' ' + gauntEnv.nebula_repo
        sh 'cp -r nebula /usr/app'
    }
}

private def install_nebula() {
    if (checkOs() == 'Windows') {
        dir('nebula')
        {
            bat 'pip install -r requirements.txt'
            bat 'python setup.py install'
        }
    }
    else {
        dir('nebula')
        {
            sh 'pip3 install -r requirements.txt'
            sh 'python3 setup.py install'
        }
    }
}

private def clone_libiio() {
    if (checkOs() == 'Windows') {
        bat 'git clone -b ' + gauntEnv.libiio_branch + ' ' + gauntEnv.libiio_repo
    }
    else {
        sh 'git clone -b ' + gauntEnv.libiio_branch + ' ' + gauntEnv.libiio_repo
        sh 'cp -r libiio /usr/app'
    }
}

private def install_libiio() {
    if (checkOs() == 'Windows') {
        dir('libiio')
        {
            bat 'mkdir build'
            bat('build')
            {
                //sh 'cmake .. -DPYTHON_BINDINGS=ON'
                bat 'cmake ..'
                bat 'cmake --build . --config Release --install'
            }
        }
    }
    else {
        dir('libiio')
        {
            sh 'mkdir build'
            dir('build')
            {
                //sh 'cmake .. -DPYTHON_BINDINGS=ON'
                sh 'cmake ..'
                sh 'make'
                sh 'make install'
                sh 'ldconfig'
            }
        }
    }
}

private def clone_telemetry(){
    if (checkOs() == 'Windows') {
        bat 'git clone -b ' + gauntEnv.telemetry_branch + ' ' + gauntEnv.telemetry_repo
    }else{
        // sh 'pip3 uninstall telemetry -y || true'
        sh 'git clone -b ' + gauntEnv.telemetry_branch + ' ' + gauntEnv.telemetry_repo
        sh 'cp -r telemetry /usr/app'
    }
}

private def install_telemetry() {
    if (checkOs() == 'Windows') {
        // bat 'git clone https://github.com/tfcollins/telemetry.git'
        dir('telemetry')
        {
            bat 'pip install -r requirements_dev.txt'
            bat 'python setup.py install'
        }
    }
    else {
        sh 'pip3 uninstall telemetry -y || true'
        // sh 'git clone https://github.com/tfcollins/telemetry.git'
        dir('telemetry')
        {
            sh 'pip3 install -r requirements_dev.txt'
            sh 'python3 setup.py install'
        }
    }
}

private def setupAgent(deps, skip_cleanup = false, docker_status) {
    try {
        def i;
        for (i = 0; i < deps.size; i++) {
            println(deps[i])
            if (deps[i] == 'nebula') {
                if (docker_status) {
                    install_nebula()
                } else {
                    clone_nebula()
                    install_nebula()
                }
            }
            if (deps[i] == 'libiio') {
                if (docker_status) {
                    install_libiio()
                } else {
                    clone_libiio()
                    install_libiio()
                }
            }
            if (deps[i] == 'telemetry') {
                if (docker_status) {
                    install_telemetry()
                } else {
                    clone_telemetry()
                    install_telemetry()
                }
            }
        }
    }
    finally {
        if (!skip_cleanup)
            cleanWs()
    }
}

private def run_i(cmd) {
    if (checkOs() == 'Windows') {
        bat cmd
    }
    else {
        sh cmd
    }
}
