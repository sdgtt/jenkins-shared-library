package sdg
import sdg.FailSafeWrapper
import sdg.NominalException

/** A map that holds all constants and data members that can be override when constructing  */
gauntEnv

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
    gauntEnv = getGauntEnv(hdlBranch, linuxBranch, bootPartitionBranch, firmwareVersion, bootfile_source)
    gauntEnv.agents_online = getOnlineAgents()
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
    println(gauntEnv.board_map)
    gauntEnv.agents = agents
    gauntEnv.boards = boards
    println(gauntEnv.agents)
    println(gauntEnv.boards)
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
                // automatically update nebula config
                if(gauntEnv.update_nebula_config){
                    stage('Update Nebula Config') {
                        run_i('if [ -d "nebula-config" ]; then rm -Rf nebula-config; fi')
                        run_i('git clone -b "' + gauntEnv.nebula_config_branch + '" ' + gauntEnv.nebula_config_repo, true)
                        if (fileExists('nebula-config/' + agent_name)){
                            run_i('sudo mv nebula-config/' + agent_name + ' /etc/default/nebula')
                        }else{
                            // create and empty file
                            run_i('sudo mv nebula-config/null-agent' + ' /etc/default/nebula')
                        }
                        
                    }
                }
                // clean up residue containers
                stage('Clean up residue docker containers') {
                    sh 'sudo docker ps -q -f status=exited | xargs --no-run-if-empty sudo docker rm'
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
                    println("Branch: " + gauntEnv.branches.toString())
                    try{
                        if (board=="pluto"){
                            if (gauntEnv.firmwareVersion == 'NA')
                                throw new Exception("Firmware must be specified")
                            nebula('dl.bootfiles --board-name=' + board 
                                    + ' --branch=' + gauntEnv.firmwareVersion 
                                    + ' --firmware', true, true, true)
                        }else{
                            if (gauntEnv.branches == ["NA","NA"])
                                throw new Exception("Either hdl_branch/linux_branch or boot_partition_branch must be specified")
                            if (gauntEnv.bootfile_source == "NA")
                                throw new Exception("bootfile_source must be specified")
                            nebula('dl.bootfiles --board-name=' + board + ' --source-root="' 
                                    + gauntEnv.nebula_local_fs_source_root 
                                    + '" --source=' + gauntEnv.bootfile_source
                                    +  ' --branch="' + gauntEnv.branches.toString() 
                                    + '"', true, true, true)
                        }
                        //get git sha properties of files
                        get_gitsha(board)
                    }catch(Exception ex){
                        throw new Exception('Downloader error: '+ ex.getMessage()) 
                    }
                    
                    //update-boot-files
                    nebula('manager.update-boot-files --board-name=' + board + ' --folder=outs', true, true, true)
                    if (board=="pluto"){
                        retry(2){
                            sleep(50)
                            nebula('uart.set-local-nic-ip-from-usbdev --board-name=' + board)
                        }
                    }
                    set_elastic_field(board, 'uboot_reached', 'True')
                    set_elastic_field(board, 'kernel_started', 'True')
                    set_elastic_field(board, 'linux_prompt_reached', 'True')
                    set_elastic_field(board, 'post_boot_failure', 'False')
                }}
                catch(Exception ex) {
                    def is_nominal_exception = false
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
                    }else if (ex.getMessage().contains('Downloader error')){
                        set_elastic_field(board, 'uboot_reached', 'False')
                        set_elastic_field(board, 'kernel_started', 'False')
                        set_elastic_field(board, 'linux_prompt_reached', 'False')
                        is_nominal_exception = true
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
                    if (is_nominal_exception)
                        throw new NominalException('UpdateBOOTFiles failed: '+ ex.getMessage())
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
                        if (gauntEnv.bootfile_source == "NA")
                            throw new Exception("bootfile_source must be specified")
                        try{
                            echo "Fetching reference boot files"
                            nebula('dl.bootfiles --board-name=' + board 
                                + ' --source-root="' + gauntEnv.nebula_local_fs_source_root 
                                + '" --source=' + gauntEnv.bootfile_source
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
                    cmd += ' jenkins_trigger ' + gauntEnv.job_trigger
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
                    def devs = []
                    def missing_devs = []
                    try {
                        // run_i('pip3 install pylibiio',true)
                        //def ip = nebula('uart.get-ip')
                        def ip = nebula('update-config network-config dutip --board-name='+board)
                        try{
                            nebula("net.check-dmesg --ip='"+ip+"' --board-name="+board)
                        }catch(Exception ex) {
                            failed_test = failed_test + "[dmesg check failed: ${ex.getMessage()}]"
                        }

                        try{
                            nebula('driver.check-iio-devices --uri="ip:'+ip+'" --board-name='+board, true, true, true)
                        }catch(Exception ex) {
                            failed_test = failed_test + "[iio_devices check failed: ${ex.getMessage()}]"
                            missing_devs = Eval.me(ex.getMessage().split('\n').last().split('not found')[1].replaceAll("'\$",""))
                            writeFile(file: board+'_missing_devs.log', text: missing_devs.join("\n"))
                            set_elastic_field(board, 'drivers_missing', missing_devs.size().toString())
                        }
                        // get drivers enumerated
                        devs = Eval.me(nebula('update-config driver-config iio_device_names -b '+board, false, true, false))
                        devs = devs.minus(missing_devs)
                        writeFile(file: board+'_enumerated_devs.log', text: devs.join("\n"))
                        set_elastic_field(board, 'drivers_enumerated', devs.size().toString())
                        
                        try{
                            if (!gauntEnv.firmware_boards.contains(board))
                                nebula("net.run-diagnostics --ip='"+ip+"' --board-name="+board, true, true, true)
                                archiveArtifacts artifacts: '*_diag_report.tar.bz2', followSymlinks: false, allowEmptyArchive: true
                        }catch(Exception ex) {
                            failed_test = failed_test + " [diagnostics failed: ${ex.getMessage()}]"
                        }

                        if(failed_test && !failed_test.allWhitespace){
                            unstable("Linux Tests Failed: ${failed_test}")
                        }
                    }catch(Exception ex) {
                        throw new NominalException(ex.getMessage())
                    }finally{
                        // count dmesg errs and warns
                        set_elastic_field(board, 'dmesg_errs', sh(returnStdout: true, script: 'cat dmesg_err_filtered.log | wc -l').trim())
                        set_elastic_field(board, 'dmesg_warns', sh(returnStdout: true, script: 'cat dmesg_warn.log | wc -l').trim())
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
                        run_i('git clone -b "' + gauntEnv.pytest_libiio_branch + '" ' + gauntEnv.pytest_libiio_repo, true)
                        dir('pytest-libiio'){
                            run_i('python3 setup.py install', true)
                        }
                        run_i('git clone -b "' + gauntEnv.pyadi_iio_branch + '" ' + gauntEnv.pyadi_iio_repo, true)
                        dir('pyadi-iio')
                        {
                            run_i('pip3 install -r requirements.txt', true)
                            run_i('pip3 install -r requirements_dev.txt', true)
                            run_i('pip3 install pylibiio', true)
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
                            cmd = "python3 -m pytest --html=testhtml/report.html --junitxml=testxml/" + board + "_reports.xml --adi-hw-map -v -k 'not stress' -s --uri='ip:"+ip+"' -m " + board_name + " --capture=tee-sys" + marker
                            def statusCode = sh script:cmd, returnStatus:true

                            // generate html report
                            if (fileExists('testhtml/report.html')){
                                publishHTML(target : [
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
                            if(fileExists('testxml/' + board + '_reports.xml')){
                                try{
                                    def pytest_logs = ['errors', 'failures', 'skipped', 'tests']
                                    pytest_logs.each {
                                        cmd = 'cat testxml/' + board + '_reports.xml | sed -rn \'s/.*' 
                                        cmd+= it + '="([0-9]+)".*/\\1/p\''
                                        set_elastic_field(board.replaceAll('_', '-'), it, sh(returnStdout: true, script: cmd).trim())
                                    }
                                    // println(gauntEnv.elastic_logs[board.replaceAll('_', '-')])
                                }catch(Exception ex){
                                    println('Parsing pytest results failed')
                                    echo getStackTrace(ex)
                                }
                            }
                            
                            // throw exception if pytest failed
                            if ((statusCode != 5) && (statusCode != 0)){
                                // Ignore error 5 which means no tests were run
                                unstable("PyADITests Failed")
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
                            run_i('git clone -b '+ gauntEnv.libad9361_iio_branch + ' ' + gauntEnv.libad9361_iio_repo, true)
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
                    }catch(Exception ex){
                        unstable("LibAD9361Tests Failed: ${ex.getMessage()}")
                    }finally{
                        dir('libad9361-iio/build'){
                            xunit([CTest(deleteOutputFiles: true, failIfNotNew: true, pattern: 'Testing/**/*.xml', skipNoTestFiles: false, stopProcessingIfError: true)])
                        }
                    }
                }else{
                    println("LibAD9361Tests: Skipping board: "+board)
                }
            }
            break
    case 'MATLABTests':
        println('Added Run MATLAB Toolbox Tests')
        cls = { String board ->
            def under_scm = true
            stage("Run MATLAB Toolbox Tests") {
                def ip = nebula('update-config network-config dutip --board-name='+board)
                sh 'cp -r /root/.matlabro /root/.matlab'
                under_scm = isMultiBranchPipeline()
                if (under_scm)
                {   
                    println("Multibranch pipeline. Checkout scm.")
                    retry(3) {
                        sleep(5)
                        checkout scm
                        sh 'git submodule update --init'
                    }
                    createMFile()
                    try{
                        sh 'IIO_URI="ip:'+ip+'" board="'+board+'" elasticserver='+gauntEnv.elastic_server+' /usr/local/MATLAB/'+gauntEnv.matlab_release+'/bin/matlab -nosplash -nodesktop -nodisplay -r "run(\'matlab_commands.m\');exit"'
                    }finally{
                        junit testResults: '*.xml', allowEmptyResults: true
                    }
                }
                else
                {   
                    println("Not a multibranch pipeline. Cloning "+gauntEnv.matlab_branch+" branch from "+gauntEnv.matlab_repo)
                    sh 'git clone --recursive -b '+gauntEnv.matlab_branch+' '+gauntEnv.matlab_repo+' Toolbox'
                    dir('Toolbox')
                    {
                        createMFile()
                        try{
                            sh 'IIO_URI="ip:'+ip+'" board="'+board+'" elasticserver='+gauntEnv.elastic_server+' /usr/local/MATLAB/'+gauntEnv.matlab_release+'/bin/matlab -nosplash -nodesktop -nodisplay -r "run(\'matlab_commands.m\');exit"'
                        }finally{
                            junit testResults: '*.xml', allowEmptyResults: true    
                        }
                    }
                }
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
            try{
                for (k = 0; k < num_stages; k++) {
                    println("Stage called for board: "+board)
                    stages[k].call(board)
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
    
    def oneNodeDocker = { agent, num_stages, stages, board, docker_image_name, enable_update_boot_pre_docker_flag, pre_docker_closure, docker_stat ->
        def k
        node(agent) {
            try {
                if (enable_update_boot_pre_docker_flag)
                    pre_docker_closure.call(board)
                docker.image(docker_image_name).inside(docker_args) {
                    try {
                        stage('Setup Docker') {
                            sh 'apt update'
                            sh 'apt-get install python3-tk -y'
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
                    }catch(NominalException ex){
                        println("oneNodeDocker: A nominal exception was encountered ${ex.getMessage()}")
                        println("Stopping execution of stages for ${board}")
                    }finally {
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
        def lock_agent = ''

        println('Agent: ' + agent + ' Board: ' + board)
        println('Number of stages to run: ' + num_stages.toString())

        if (gauntEnv.lock_agent) {
            println('Locking agent: '+agent+'. Effectively only one test executor is running on the agent.')
            lock_agent = agent
        }
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
                    lock(lock_agent){
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
                    }
                 };
            }else{
                jobs[agent + '-' + board] = {
                    lock(lock_agent){ 
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
            }
            
        } else{
            jobs[agent + '-' + board] = { oneNode(agent, num_stages, stages, board, docker_status) };
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
 * Set lock_agent. Set to true to effectively use just one test executor on agents
 * @param lock_agent Boolean true to enable
 */
def set_lock_agent(lock_agent) {
    gauntEnv.lock_agent = lock_agent
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
 * Set list of MATLAB commands
 * @param matlab_commands list of strings of commands to be executed in MATLAB
 * For example: "runHWTests('AD9361')"
 */
def set_matlab_commands(List matlab_commands) {
    assert matlab_commands instanceof java.util.List
    gauntEnv.matlab_commands = matlab_commands
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
def isMultiBranchPipeline() {
    isMultiBranch = false
    println("Checking if multibranch pipeline..")
    try
    {
        checkout scm
        isMultiBranch = true
    }
    catch(all)
    {
        println("Not a multibranch pipeline")
    }
    return isMultiBranch
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

private def check_required_hardware() {

    stage('Check Required Hardware'){
        def s = gauntEnv.required_hardware.size()
        def b = gauntEnv.boards.size()
        def rh = gauntEnv.required_hardware
        def filtered_board_list = []
        def filtered_agent_list = []

        println("Found boards:")
        for (k = 0; k < b; k++) {
            println("Agent: "+gauntEnv.agents[k]+" Board: "+gauntEnv.boards[k])
            if (gauntEnv.required_hardware.contains(gauntEnv.boards[k])){
                filtered_board_list.add(gauntEnv.boards[k])
                filtered_agent_list.add(gauntEnv.agents[k])
                rh.remove(rh.indexOf(gauntEnv.boards[k]))
            }// else do nothing
        }
        gauntEnv.boards = filtered_board_list
        gauntEnv.agents = filtered_agent_list

        if(rh.size() > 0){
            println("Some required hardwares cannot be found :" + rh.toString())
            currentBuild.result = "UNSTABLE"
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
    def script_out = ''
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
            }catch(Exception ex){
                if (fileExists(outfile)){
                    script_out = readFile(outfile).trim()
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
        run_i('git clone -b '+  gauntEnv.nebula_branch + ' ' + gauntEnv.nebula_repo, true)
    }
    else {
        sh 'pip3 uninstall nebula -y || true'
        run_i('git clone -b ' + gauntEnv.nebula_branch + ' ' + gauntEnv.nebula_repo, true)
        sh 'cp -r nebula /usr/app'
    }
}

private def install_nebula() {
    if (checkOs() == 'Windows') {
        dir('nebula')
        {
            run_i('pip install -r requirements.txt', true)
            run_i('python setup.py install', true)
        }
    }
    else {
        dir('nebula')
        {
            run_i('pip3 install -r requirements.txt', true)
            run_i('python3 setup.py install', true)
        }
    }
}

private def clone_libiio() {
    if (checkOs() == 'Windows') {
        run_i('git clone -b ' + gauntEnv.libiio_branch + ' ' + gauntEnv.libiio_repo, true)
    }
    else {
        run_i('git clone -b ' + gauntEnv.libiio_branch + ' ' + gauntEnv.libiio_repo, true)
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
                bat 'cmake .. -DPYTHON_BINDINGS=ON -DHAVE_DNS_SD=OFF'
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
                sh 'cmake .. -DPYTHON_BINDINGS=ON -DHAVE_DNS_SD=OFF'
                sh 'make'
                sh 'make install'
                sh 'ldconfig'
                // install python bindings
                dir('bindings/python'){
                    sh 'python3 setup.py install'
                }
            }

            

        }
    }
}

private def clone_telemetry(){
    if (checkOs() == 'Windows') {
        run_i('git clone -b ' + gauntEnv.telemetry_branch + ' ' + gauntEnv.telemetry_repo, true)
    }else{
        // sh 'pip3 uninstall telemetry -y || true'
        run_i('git clone -b ' + gauntEnv.telemetry_branch + ' ' + gauntEnv.telemetry_repo, true)
        sh 'cp -r telemetry /usr/app'
    }
}

private def install_telemetry() {
    if (checkOs() == 'Windows') {
        // bat 'git clone https://github.com/tfcollins/telemetry.git'
        dir('telemetry')
        {
            run_i('pip install elasticsearch', true)
            run_i('python setup.py install', true)
        }
    }
    else {
        run_i('pip3 uninstall telemetry -y || true', true)
        // sh 'git clone https://github.com/tfcollins/telemetry.git'
        dir('telemetry')
        {
            run_i('pip3 install elasticsearch', true)
            run_i('python3 setup.py install', true)
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
    if (gauntEnv.nebula_local_fs_source_root == "local_fs"){
        set_elastic_field(board, 'hdl_hash', 'NA')
        set_elastic_field(board, 'linux_hash', 'NA')
        return
    }

    if (gauntEnv.firmware_boards.contains(board)){
        set_elastic_field(board, 'hdl_hash', 'NA')
        set_elastic_field(board, 'linux_hash', 'NA')
        return
    }
    
    dir ('outs'){
        script{ properties = readYaml file: 'properties.yaml' }
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
    def marker = ''
    def board_name = board
    if (board.contains("-v")){
        board_name = board.split("-v")[0]
        marker = ' --' + board.split("-v")[1]
        return [board_name:board_name, marker:marker]
    }
    else {
        return [board_name:board_name, marker:marker]
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

private def run_i(cmd, do_retry=false) {
    def retry_count = 1
    if(do_retry){
        retry_count = gauntEnv.max_retry
    }
    retry(retry_count){
        if (checkOs() == 'Windows') {
            bat cmd
        }
        else {
            sh cmd
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
    writeFile file: 'matlab_commands.m', text: command_oneline
    sh 'ls -l matlab_commands.m'
    sh 'cat matlab_commands.m'
}