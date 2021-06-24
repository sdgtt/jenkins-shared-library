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
            firmware_boards: ['pluto','m2k'],
            enable_docker: false,
            docker_image: 'tfcollins/sw-ci:latest',
            docker_args: ['MATLAB','Vivado'],
            docker_host_mode: true,
            enable_update_boot_pre_docker: false,
            board_sub_categories : ['rx2tx2'],
            enable_resource_queuing: false,
            setup_called: false,
            nebula_debug: false,
            nebula_local_fs_source_root: '/var/lib/tftpboot',
            elastic_server: '',
            iio_uri_source: 'ip',
            iio_uri_baudrate: 921600,
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
            telemetry_branch: 'master',
            send_results: false,
            elastic_logs : [:]
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
                    //check_for_marker(board)
                    if (board=="pluto")
                        nebula('dl.bootfiles --board-name=' + board + ' --branch=' + gauntEnv.firmwareVersion + ' --firmware', true, true, true)
                    else
                        nebula('dl.bootfiles --board-name=' + board + ' --source-root="' + gauntEnv.nebula_local_fs_source_root + '" --source=' + gauntEnv.bootfile_source
                                +  ' --branch="' + gauntEnv.branches.toString() + '"', true, true, true)
                    //get git sha properties of files
                    get_gitsha(board)
                    //update-boot-files
                    nebula('manager.update-boot-files --board-name=' + board + ' --folder=outs', true, true, true)
                    if (board=="pluto")
                        nebula('uart.set-local-nic-ip-from-usbdev --board-name=' + board)
                    set_elastic_field(board, 'uboot_reached', 'True')
                    set_elastic_field(board, 'kernel_started', 'True')
                    set_elastic_field(board, 'linux_prompt_reached', 'True')
                    set_elastic_field(board, 'post_boot_failure', 'False')
                }}
                catch(Exception ex) {
                    echo getStackTrace(ex)
                    if (ex.getMessage().contains('u-boot not reached')){
                        set_elastic_field(board, 'uboot_reached', 'False')
                        set_elastic_field(board, 'kernel_started', 'False')
                        set_elastic_field(board, 'linux_prompt_reached', 'False')
                    }else if (ex.getMessage().contains('u-boot menu cannot boot kernel')){
                        set_elastic_field(board, 'uboot_reached', 'True')
                        set_elastic_field(board, 'kernel_started', 'False')
                        set_elastic_field(board, 'linux_prompt_reached', 'False')
                    }else if (ex.getMessage().contains('Linux not fully booting')){
                        set_elastic_field(board, 'uboot_reached', 'True')
                        set_elastic_field(board, 'kernel_started', 'True')
                        set_elastic_field(board, 'linux_prompt_reached', 'False')
                    }else if (ex.getMessage().contains('Linux is functional but Ethernet is broken after updating boot files') ||
                              ex.getMessage().contains('SSH not working but ping does after updating boot files')){
                        set_elastic_field(board, 'uboot_reached', 'True')
                        set_elastic_field(board, 'kernel_started', 'True')
                        set_elastic_field(board, 'linux_prompt_reached', 'True')
                        set_elastic_field(board, 'post_boot_failure', 'True')
                    }else{
                        echo "Update BOOT Files unexpectedly failed. ${ex.getMessage()}"
                    }
                    get_gitsha(board)
                    // send logs to elastic
                    if (gauntEnv.send_results){
                        set_elastic_field(board, 'last_failing_stage', 'UpdateBOOTFiles')
                        failing_msg = "'" + ex.getMessage().split('\n').last().replaceAll( /(['])/, '"') + "'" 
                        set_elastic_field(board, 'last_failing_stage_failure', failing_msg)
                        stage_library('SendResults').call(board)
                    }
                    throw new Exception('UpdateBOOTFiles failed: '+ ex.getMessage())
                }finally{
                    //archive uart logs
                    run_i("if [ -f ${board}.log ]; then mv ${board}.log uart_boot_" + board + ".log; fi")
                    archiveArtifacts artifacts: 'uart_boot_*.log', followSymlinks: false, allowEmptyArchive: true
                }
      };
            break
    
    case 'RecoverBoard':
        println('Added Stage RecoverBoard')
        cls = { String board ->
            stage('RecoverBoard'){
                echo "Recovering ${board}"
                def ref_branch = ['boot_partition', 'release']
                if (board=="pluto"){
                    echo "Recover stage does not support pluto yet!"
                }else{
                    dir ('recovery'){
                        try{
                            echo "Fetching reference boot files"
                            //check_for_marker(board)
                            nebula('dl.bootfiles --board-name=' + board + ' --source-root="' + gauntEnv.nebula_local_fs_source_root + '" --source=' + gauntEnv.bootfile_source
                                +  ' --branch="' + ref_branch.toString() + '"') 
                            echo "Extracting reference fsbl and u-boot"
                            dir('outs'){
                                sh("cp bootgen_sysfiles.tgz ..")
                            }
                            sh("tar -xzvf bootgen_sysfiles.tgz; cp u-boot-*.elf u-boot.elf")
                            echo "Executing board recovery..."
                            nebula('manager.recovery-device-manager --board-name=' + board + ' --folder=outs' + ' --sdcard')
                        }catch(Exception ex){
                            echo getStackTrace(ex)
                            throw ex
                        }finally{
                            //archive uart logs
                            run_i("if [ -f ${board}.log ]; then mv ${board}.log uart_recover_" + board + ".log; fi")
                            archiveArtifacts artifacts: 'uart_recover_*.log', followSymlinks: false, allowEmptyArchive: true
                        }
                    }
                }
            }
        };
            break
    case 'RestoreIP':
        println('Added stage RestoreIP')
        cls = { String board ->
            stage('Restore IP'){
                echo "Restoring IP of ${board}"
                if (board=="pluto"){
                    echo "Pluto not yet supported"
                }else{
                    // prepare environment
                    def dutip = ''
                    def uart_ip = ''
                    try{
                        dutip = nebula('update-config network-config dutip --board-name=' + board )
                        uart_ip = nebula('uart.get-ip --board-name=' + board )
                        if (dutip != uart_ip){
                            echo "${uart_ip} not same with config ip ${dutip}, will proceed to reconfiguration..."
                            nebula("uart.set-static-ip --ip=${dutip} --board-name=${board}", false, true)
                            // nebula('uart.restart-board --board-name=' + board , false, true)
                            // wait for a little time before intface completes restart
                            echo "waiting for interface..."
                            sleep(10)
                            // verify new address
                            uart_ip = nebula('uart.get-ip --board-name=' + board )
                            if (dutip != uart_ip)
                                throw new Exception('IP reconfiguration failed.')
                            else
                                echo "IP reconfiguration successful!"
                        }else{
                            echo "${uart_ip} is same with config ip ${dutip}"
                        }
                    }catch(Exception ex){
                        echo getStackTrace(ex)
                        dutip = ''
                        uart_ip = ''
                        throw ex
                    }
                }
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
                    is_hdl_release = "False"
                    is_linux_release = "False"
                    is_boot_partition_release = "False"
                    if (gauntEnv.bootPartitionBranch == 'NA'){
                        is_hdl_release = ( gauntEnv.hdlBranch == "release" )? "True": "False"
                        is_linux_release = ( gauntEnv.linuxBranch == "release" )? "True": "False"
                    }else{
                        is_boot_partition_release = ( gauntEnv.bootPartitionBranch == "release" )? "True": "False"
                    }
                    println(gauntEnv.elastic_logs)
                    echo 'Starting send log to elastic search'
                    cmd = 'boot_folder_name ' + board
                    cmd += ' hdl_hash ' + '\'' + get_elastic_field(board, 'hdl_hash' , 'NA') + '\''
                    cmd += ' linux_hash ' +  '\'' + get_elastic_field(board, 'linux_hash' , 'NA') + '\''
                    cmd += ' boot_partition_hash ' + '\'' + gauntEnv.boot_partition_hash + '\''
                    cmd += ' hdl_branch ' + gauntEnv.hdlBranch
                    cmd += ' linux_branch ' + gauntEnv.linuxBranch
                    cmd += ' boot_partition_branch ' + gauntEnv.bootPartitionBranch
                    cmd += ' is_hdl_release ' + is_hdl_release
                    cmd += ' is_linux_release '  +  is_linux_release
                    cmd += ' is_boot_partition_release ' + is_boot_partition_release
                    cmd += ' uboot_reached ' + get_elastic_field(board, 'uboot_reached', 'False')
                    cmd += ' linux_prompt_reached ' + get_elastic_field(board, 'linux_prompt_reached', 'False')
                    cmd += ' drivers_enumerated ' + get_elastic_field(board, 'drivers_enumerated', '0')
                    cmd += ' drivers_missing ' + get_elastic_field(board, 'drivers_missing', '0')
                    cmd += ' dmesg_warnings_found ' + get_elastic_field(board, 'dmesg_warns' , '0')
                    cmd += ' dmesg_errors_found ' + get_elastic_field(board, 'dmesg_errs' , '0')
                    // cmd +="jenkins_job_date datetime.datetime.now(),
                    cmd += ' jenkins_build_number ' + env.BUILD_NUMBER
                    cmd += ' jenkins_project_name ' + env.JOB_NAME
                    cmd += ' jenkins_agent ' + env.NODE_NAME
                    cmd += ' pytest_errors ' + get_elastic_field(board, 'errors', '0')
                    cmd += ' pytest_failures ' + get_elastic_field(board, 'failures', '0')
                    cmd += ' pytest_skipped ' + get_elastic_field(board, 'skipped', '0')
                    cmd += ' pytest_tests ' + get_elastic_field(board, 'tests', '0')
                    cmd += ' last_failing_stage ' + get_elastic_field(board, 'last_failing_stage', 'NA')
                    cmd += ' last_failing_stage_failure ' + get_elastic_field(board, 'last_failing_stage_failure', 'NA')
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
                            nebula('driver.check-iio-devices --uri="ip:'+ip+'" --board-name='+board, true, true, true)
                        }catch(Exception ex) {
                            failed_test = failed_test + " [iio_devices check failed: $ex]"
                            missing_devs = Eval.me(ex.getMessage().split('\n').last().split('not found')[1].replaceAll("'\$",""))
                            set_elastic_field(board, 'drivers_missing', missing_devs.size().toString())
                        }
                        if(failed_test && !failed_test.allWhitespace){
                            throw new Exception("failed_test")
                        }
                    }catch(Exception ex) {
                        echo getStackTrace(ex)
                        throw new NominalException("Linux Test Failed: $ex")
                    }finally{
                        // count dmesg errs and warns
                        set_elastic_field(board, 'dmesg_errs', sh(returnStdout: true, script: 'cat dmesg_err_filtered.log | wc -l').trim())
                        set_elastic_field(board, 'dmesg_warns', sh(returnStdout: true, script: 'cat dmesg_warn.log | wc -l').trim())
                        println('Dmesg warns: ' + get_elastic_field(board, 'dmesg_warns'))
                        println('Dmesg errs: ' + get_elastic_field(board, 'dmesg_errs'))
                        // Rename logs
                        run_i("if [ -f dmesg.log ]; then mv dmesg.log dmesg_" + board + ".log; fi")
                        run_i("if [ -f dmesg_err_filtered.log ]; then mv dmesg_err_filtered.log dmesg_" + board + "_err.log; fi")
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
                        def serial = nebula('update-config uart-config address --board-name='+board)
                        def uri;
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
                            run_i('mkdir testhtml')
                            if (gauntEnv.iio_uri_source == "ip")
                                uri = "ip:" + ip;
                            else
                                uri = "serial:" + serial + "," + gauntEnv.iio_uri_baudrate.toString()
                            check = check_for_marker(board)
                            board = board.replaceAll('-', '_')
                            board_name = check.board_name.replaceAll('-', '_')
                            marker = check.marker
                            cmd = "python3 -m pytest --html=testhtml/report.html --junitxml=testxml/" + board + "_reports.xml --adi-hw-map -v -k 'not stress' -s --uri='ip:"+ip+"' -m " + board_name + " --capture=tee-sys" + " --" + marker
                            def statusCode = sh script:cmd, returnStatus:true
                            publishHTML(target : [escapeUnderscores: false, allowMissing: false, alwaysLinkToLastBuild: false, keepAll: true, reportDir: 'testhtml', reportFiles: 'report.html', reportName: board, reportTitles: board])
                            // get pytest results for logging
                            try{
                                def pytest_logs = ['errors', 'failures', 'skipped', 'tests']
                                pytest_logs.each {
                                    cmd = 'cat testxml/' + board + '_reports.xml | sed -rn \'s/.*' 
                                    cmd+= it + '="([0-9]+)".*/\\1/p\''
                                    println(cmd)
                                    set_elastic_field(board.replaceAll('_', '-'), it, sh(returnStdout: true, script: cmd).trim())
                                }
                                println(gauntEnv.elastic_logs[board])
                            }catch(Exception ex){
                                println(ex)
                                throw new NominalException('PyADITests Failed')
                            }
                            
                            if ((statusCode != 5) && (statusCode != 0)){
                                // Ignore error 5 which means no tests were run
                                throw new NominalException('PyADITests Failed')
                            }                
                        }
                    }
                    finally
                    {
                        // archiveArtifacts artifacts: 'pyadi-iio/testxml/*.xml', followSymlinks: false, allowEmptyArchive: true
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

private def run_agents() {
    // Start stages for each node with a board
    def docker_status = gauntEnv.enable_docker
    def jobs = [:]
    def num_boards = gauntEnv.boards.size()
    def docker_args = getDockerConfig(gauntEnv.docker_args)
    def enable_update_boot_pre_docker = gauntEnv.enable_update_boot_pre_docker
    def enable_resource_queuing = gauntEnv.enable_resource_queuing
    def pre_docker_cls = stage_library("UpdateBOOTFiles")
    docker_args.add('-v /etc/default:/default:ro')
    docker_args.add('-v /dev:/dev')
    docker_args.add('-v /usr/app:/app')
    if (gauntEnv.docker_host_mode) {
        docker_args.add('--network host')
    }
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
            if( enable_resource_queuing ){
                println("Enable resource queueing")
                jobs[agent + '-' + board] = {
                    def lock_name = extractLockName(board)
                    echo "Acquiring lock for ${lock_name}"
                    lock(lock_name){
                        oneNodeDocker(
                            agent,
                            num_stages,
                            stages,
                            board,
                            docker_image,
                            enable_update_boot_pre_docker,
                            pre_docker_cls, 
                            docker_status
                        )
                    }
                 };
            }else{
                jobs[agent + '-' + board] = { 
                    oneNodeDocker(
                            agent,
                            num_stages,
                            stages,
                            board,
                            docker_image,
                            enable_update_boot_pre_docker,
                            pre_docker_cls,
                            docker_status
                        )
                 };
            }
            
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
 * Set enable_resource_queuing. Set enable_resource_queuing. Set to true to enable
 * @param enable_resource_queuing Boolean true to enable
 */
def set_enable_resource_queuing(enable_resource_queuing) {
    gauntEnv.enable_resource_queuing = enable_resource_queuing
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

def nebula(cmd, full=false, show_log=false, report_error=false) {
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
        if (report_error){
            def outfile = 'out.out'
            def nebula_traceback = []
            cmd = cmd + " 2>&1 | tee ${outfile}"
            cmd = 'set -o pipefail; ' + cmd 
            try{
                sh cmd
                if (fileExists(outfile))
                    script_out = readFile(outfile).trim()
                    echo script_out
            }catch(Exception ex){
                echo ex.getMessage()
                if (fileExists(outfile)){
                    script_out = readFile(outfile).trim()
                    echo script_out
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
            script_out = sh(script: cmd, returnStdout: true).trim()
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
            bat 'pip install elasticsearch'
            bat 'python setup.py install'
        }
    }
    else {
        sh 'pip3 uninstall telemetry -y || true'
        // sh 'git clone https://github.com/tfcollins/telemetry.git'
        dir('telemetry')
        {
            sh 'pip3 install elasticsearch'
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

private def get_gitsha(String board){
    dir ('outs'){
        script{ properties = readYaml file: 'properties.yaml' }
    }
    if (gauntEnv.firmware_boards.contains(board)){
        set_elastic_field(board, 'hdl_hash', 'NA')
        set_elastic_field(board, 'linux_hash', 'NA')
        return
    }

    if (gauntEnv.bootPartitionBranch == 'NA'){
        hdl_hash = properties.hdl_git_sha + " (" + properties.hdl_folder + ")"
        linux_hash = properties.linux_git_sha + " (" + properties.linux_folder + ")"
        set_elastic_field(board, 'hdl_hash', hdl_hash)
        set_elastic_field(board, 'linux_hash', linux_hash)
    }else{
        hdl_hash = properties.hdl_git_sha + " (" + properties.bootpartition_folder + ")"
        linux_hash = properties.linux_git_sha + " (" + properties.bootpartition_folder + ")"
        set_elastic_field(board, 'hdl_hash', hdl_hash)
        set_elastic_field(board, 'linux_hash', linux_hash)
    }
}

private def check_for_marker(String board){
    if (board.contains("-v")){
        board_name = board.split("-v")[0]
        marker = board.split("-v")[1]
        return [board_name:board_name, marker:marker]
    }
    else {
        board_name = board
        return board_name
    }
}

private def extractLockName(String bname){
    echo "Extracting resource lockname from ${bname}"
    def lockName = bname
    if (bname.contains("-v")){
        lockName = bname.split("-v")[0]
    }
    for (cat in gauntEnv.board_sub_categories){
        if(lockName.contains('-' + cat))
            lockName = lockName.replace('-' + cat, "")
    }
    return lockName
}

private def run_i(cmd) {
    if (checkOs() == 'Windows') {
        bat cmd
    }
    else {
        sh cmd
    }
}

private def String getStackTrace(Throwable aThrowable){
    // Utility method to print the stack trace of an error
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    PrintStream ps = new PrintStream(baos, true);
    aThrowable.printStackTrace(ps);
    return baos.toString();
}
