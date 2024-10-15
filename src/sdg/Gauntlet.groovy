package sdg
import sdg.FailSafeWrapper
import sdg.NominalException
import org.jenkinsci.plugins.pipeline.modeldefinition.Utils

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
                stage('Update agents') {
                    def deps = check_update_container_lib(update_container_lib)
                    setupAgent(deps, false, update_requirements)
                }
                // automatically update nebula config
                if(gauntEnv.update_nebula_config){
                    stage('Update Nebula Config') {
                        run_i('sudo rm -rf nebula-config')
                        if(gauntEnv.nebula_config_source == 'github'){
                            run_i('git clone -b "' + gauntEnv.nebula_config_branch + '" ' + gauntEnv.nebula_config_repo, true)
                        }else if(gauntEnv.nebula_config_source == 'netbox'){
                            run_i('mkdir nebula-config')
                            dir('nebula-config'){
                                def custom = ""
                                if(gauntEnv.netbox_include_variants == false){
                                    custom = custom + " --no-include-variants"
                                }
                                if(gauntEnv.netbox_include_children == false){
                                    custom = custom + " --no-include-children"
                                }
                                if(gauntEnv.netbox_test_agent == true){
                                    agent = "" 
                                }else{
                                    agent = agent_name
                                }
                                nebula('gen-config-netbox --jenkins-agent=' + agent
                                    + ' --netbox-ip=' + gauntEnv.netbox_ip
                                    + ' --netbox-port=' + gauntEnv.netbox_port
                                    + ' --netbox-baseurl=' + gauntEnv.netbox_base_url
                                    + ' --netbox-token=' + gauntEnv.netbox_token
                                    + ' --devices-tag=' + gauntEnv.netbox_devices_tag
                                    + ' --template=' + gauntEnv.netbox_nebula_template
                                    + custom
                                    + ' --outfile='+ agent_name, true, true, false)
                            }
                        }else{
                            println(gauntEnv.nebula_config_source + ' as config source is not supported yet.')
                        }
                        
                        if (fileExists('nebula-config/' + agent_name)){
                            run_i('sudo mv nebula-config/' + agent_name + ' /etc/default/nebula')
                        }else{
                            // create and empty file
                            run_i('sudo mv nebula-config/null-agent' + ' /etc/default/nebula')
                        }
                        
                    }
                }
                // clean up residue containers and detached screen sessions
                stage('Clean up residue docker containers') {
                    sh 'sudo docker ps -q -f status=exited | xargs --no-run-if-empty sudo docker rm'
                    sh 'sudo screen -ls | grep Detached | cut -d. -f1 | awk "{print $1}" | sudo xargs -r kill' //close all detached screen session on the agent
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
            cls = { String board, ml_bootbin_case=null ->
                try {
                    stage('Update BOOT Files') {
                        def boolean trxPluto = gauntEnv.docker_args.contains("MATLAB") && (board=="pluto")
                        println trxPluto
                        if (trxPluto) {
                            println("Skip pluto firmware update.")
                            Utils.markStageSkippedForConditional('Update BOOT Files')
                        }else{
                            println("Board name passed: "+board)
                            println("Branch: " + gauntEnv.branches.toString())
                            try{
                                if (gauntEnv.toolbox_generated_bootbin) {
                                    println("MATLAB BOOT.BIN job variation: "+ml_bootbin_case)
                                    println("Downloading bootbin generated from toolbox")
                                    nebula('show-log dl.matlab-bootbins'+
                                         ' -t "'+gauntEnv.ml_toolbox+
                                        '" -b "'+gauntEnv.ml_branch+
                                        '" -u "'+gauntEnv.ml_build+'"')
                                }
                                if (board=="pluto"){
                                    if (gauntEnv.firmwareVersion == 'NA')
                                        throw new Exception("Firmware must be specified")
                                    nebula('dl.bootfiles --board-name=' + board 
                                            + ' --source="github"'
                                            +  ' --branch="' + gauntEnv.firmwareVersion  
                                            +  '" --filetype="firmware"', true, true, true)
                                }else{
                                    if (gauntEnv.branches == ["NA","NA"])
                                        throw new Exception("Either hdl_branch/linux_branch or boot_partition_branch must be specified")
                                    if (gauntEnv.bootfile_source == "NA")
                                        throw new Exception("bootfile_source must be specified")
                                    nebula('dl.bootfiles --board-name=' + board 
                                            + ' --source-root="' + gauntEnv.nebula_local_fs_source_root 
                                            + '" --source=' + gauntEnv.bootfile_source
                                            +  ' --branch="' + gauntEnv.branches.toString()
                                            +  '"' + gauntEnv.filetype, true, true, true) 
                                }
                                //get git sha properties of files
                                get_gitsha(board)
                            }catch(Exception ex){
                                throw new Exception('Downloader error: '+ ex.getMessage()) 
                            }
                            
                            if(gauntEnv.toolbox_generated_bootbin) {
                                println("Replace bootbin with one generated from toolbox")
                                // Get list of files in ml_bootbins folder
                                def ml_bootfiles = sh (script: "ls   ml_bootbins", returnStdout: true).trim()
                                println("ml_bootfiles: " + ml_bootfiles)
                                // Filter bootbin for specific case (rx,tx,rxtx)
                                def found = false;
                                for (String bootfile : ml_bootfiles.split("\\r?\\n")) {
                                    println("Inspecting " + bootfile + " for " + ml_bootbin_case + "_BOOT.BIN")
                                    println("Must contain board: " + board)
                                    println(bootfile.contains(board) && bootfile.contains("_"+ml_bootbin_case+"_BOOT.BIN"))
                                    if (bootfile.contains(board) && bootfile.contains("_"+ml_bootbin_case+"_BOOT.BIN")) {
                                        // Copy bootbin to outs folder
                                        println("Copy " + bootfile + " to outs folder")
                                        sh "cp ml_bootbins/${bootfile} outs/BOOT.BIN"
                                        found = true;
                                        break
                                    }
                                }
                                if (!found) {
                                    println("No bootbin found for " + ml_bootbin_case + " case")
                                    println("Skipping Update BOOT Files stage")
                                    println("Skipping "+gauntEnv.ml_test_stages.toString()+" related test stages")
                                    gauntEnv.internal_stages_to_skip[board] = gauntEnv.ml_test_stages;
                                    return;
                                }
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
                        }
                    }
                }
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
                    failing_msg = "'" + ex.getMessage().split('\n').last().replaceAll( /(['])/, '"') + "'"
                    // send logs to elastic
                    if (gauntEnv.send_results){
                        set_elastic_field(board, 'last_failing_stage', 'UpdateBOOTFiles')
                        set_elastic_field(board, 'last_failing_stage_failure', failing_msg)
                        stage_library('SendResults').call(board)
                    }
                    if (is_nominal_exception)
                        throw new NominalException('UpdateBOOTFiles failed: '+ ex.getMessage())
                    // log Jira
                    try{
                        description = failing_msg
                    }catch(Exception desc){
                        println('Error updating description.')
                    }finally{
                        logJira([board:board, summary:'Update BOOT files failed.', description:description, attachment:[board+".log"]]) 
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
                                +  ' --branch="' + ref_branch.toString()
                                +  '" --filetype="boot_partition"', true, true, true)
                            echo "Extracting reference fsbl and u-boot"
                            dir('outs'){
                                sh("cp bootgen_sysfiles.tgz ..")
                            }
                            sh("tar -xzvf bootgen_sysfiles.tgz; cp u-boot*.elf u-boot.elf")
                            echo "Executing board recovery..."
                            nebula(nebula_cmd)
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
                    cmd += ' jenkins_project_name ' + '\'' + env.JOB_NAME + '\''
                    cmd += ' jenkins_agent ' + env.NODE_NAME
                    cmd += ' jenkins_trigger ' + gauntEnv.job_trigger
                    cmd += ' pytest_errors ' + get_elastic_field(board, 'pytest_errors', '0')
                    cmd += ' pytest_failures ' + get_elastic_field(board, 'pytest_failures', '0')
                    cmd += ' pytest_skipped ' + get_elastic_field(board, 'pytest_skipped', '0')
                    cmd += ' pytest_tests ' + get_elastic_field(board, 'pytest_tests', '0')
                    cmd += ' matlab_errors ' + get_elastic_field(board, 'matlab_errors', '0')
                    cmd += ' matlab_failures ' + get_elastic_field(board, 'matlab_failures', '0')
                    cmd += ' matlab_skipped ' + get_elastic_field(board, 'matlab_skipped', '0')
                    cmd += ' matlab_tests ' + get_elastic_field(board, 'matlab_tests', '0')
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
                            sh 'iio_info --uri=ip:'+ip
                            nebula("net.check-dmesg --ip='"+ip+"' --board-name="+board)
                        }catch(Exception ex) {
                            failed_test = failed_test + "[dmesg check failed: ${ex.getMessage()}]"
                        }
                        
                        try{
                            if (!gauntEnv.firmware_boards.contains(board)){
                                try{
                                    nebula('update-config board-config serial --board-name='+board)
                                    nebula("net.run-diagnostics --ip='"+ip+"' --board-type=rpi --board-name="+board, true, true, true)
                                }catch(Exception ex){
                                    nebula("net.run-diagnostics --ip='"+ip+"' --board-name="+board, true, true, true)
                                }
                                archiveArtifacts artifacts: '*_diag_report.tar.bz2', followSymlinks: false, allowEmptyArchive: true
                            }
                        }catch(Exception ex) {
                            failed_test = failed_test + " [diagnostics failed: ${ex.getMessage()}]"
                        }

                        if(failed_test && !failed_test.allWhitespace){
                            // log Jira
                            def description = ""
                            try{
                                description += "*Missing drivers: " + missing_devs.size().toString() + "* (" + missing_devs.join(", ") + ")\n"
                                dmesg_errs = readFile("dmesg_err_filtered.log").readLines()
                                description += "*dmesg errors: ${dmesg_errs.size()}*\n" + dmesg_errs.join("\n")
                            }catch(Exception desc){
                                println('Error updating description.')
                            }finally{
                                logJira([board:board, summary:'Linux tests failed.', description:description, attachment:[board+"_diag_report.tar.bz2","dmesg.log"]]) 
                            }
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
                        def ip;
                        def serial;
                        def uri;
                        def description = ""
                        def pytest_attachment = null
                        println('IP: ' + ip)
                        // temporarily get pytest-libiio from another source
                        run_i('git clone -b "' + gauntEnv.pytest_libiio_branch + '" ' + gauntEnv.pytest_libiio_repo, true)
                        dir('pytest-libiio'){
                            run_i('python3 setup.py install', true)
                        }
                        //install libad9361 python bindings
                        try{
                            sh 'python3 -c "import ad9361"'
                        }catch (Exception ex){
                            run_i('sudo rm -rf libad9361-iio')
                            run_i('git clone -b '+ gauntEnv.libad9361_iio_branch + ' ' + gauntEnv.libad9361_iio_repo, true)
                            dir('libad9361-iio'){
                                sh 'mkdir -p build'
                                dir('build'){
                                    sh 'sudo cmake -DPYTHON_BINDINGS=ON ..'
                                    sh 'sudo make'
                                    sh 'sudo make install'
                                    sh 'ldconfig'
                                }
                            }
                        }
                        //scm pyadi-iio
                        dir('pyadi-iio'){
                            under_scm = isMultiBranchPipeline()
                            if (under_scm){
                                 println("Multibranch pipeline. Checkout scm")
                            }else{
                                println("Not a multibranch pipeline. Cloning "+gauntEnv.pyadi_iio_branch+" branch from "+gauntEnv.pyadi_iio_repo)
                                run_i('git clone -b "' + gauntEnv.pyadi_iio_branch + '" ' + gauntEnv.pyadi_iio_repo+' .', true)
                            }
                        }
                        dir('pyadi-iio')
                        {
                            run_i('pip3 install -r requirements.txt', true)
                            run_i('pip3 install -r requirements_dev.txt', true)
                            run_i('pip3 install pylibiio', true)
                            run_i('mkdir testxml')
                            run_i('mkdir testhtml')
                            if (gauntEnv.iio_uri_source == "ip"){
                                ip = nebula('update-config network-config dutip --board-name='+board)
                                uri = "ip:" + ip;
                            }else{
                                serial = nebula('update-config uart-config address --board-name='+board)
                                uri = "serial:" + serial + "," + gauntEnv.iio_uri_baudrate.toString()
                            }
                            check = check_for_marker(board)
                            board = board.replaceAll('-', '_')
                            board_name = check.board_name.replaceAll('-', '_')
                            marker = check.marker
                            cmd = "python3 -m pytest --html=testhtml/report.html --junitxml=testxml/" + board + "_reports.xml"
                            cmd += " --adi-hw-map -v -k 'not stress and not prod' -s --uri="+uri+" -m " + board_name
                            cmd += " --scan-verbose --capture=tee-sys" + marker
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
                            xmlFile = 'testxml/' + board + '_reports.xml'
                            if(fileExists(xmlFile)){
                                try{
                                    parseForLogging ('pytest', xmlFile, board)
                                }catch(Exception ex){
                                    println('Parsing pytest results failed')
                                    echo getStackTrace(ex)
                                }
                                pytest_attachment = board+"_reports.xml"
                            }
                            
                            // throw exception if pytest failed
                            if ((statusCode != 5) && (statusCode != 0)){
                                // Ignore error 5 which means no tests were run
                                // log Jira
                                dir('testxml'){
                                    try{
                                        sh 'grep \" name=.*<failure\" *.xml | sed \'s/.*name=\"\\(.*\\)" .*<failure.*/\\1/\' > failures.txt'
                                        description += readFile 'failures.txt'
                                    }catch(Exception desc){
                                        println('Error updating description.')
                                    }finally{
                                        logJira([board:board, summary:'PyADI tests failed.', description: description, attachment:[pytest_attachment]])  
                                    }
                                } 
                                unstable("PyADITests Failed")
                            }                
                        }
                    }
                    finally
                    {
                        archiveArtifacts artifacts: 'pyadi-iio/testxml/*.xml', followSymlinks: false, allowEmptyArchive: true
                        junit testResults: 'pyadi-iio/testxml/*.xml', allowEmptyResults: true                    
                    }
                }
            }
            break
    case 'LibAD9361Tests':
            cls = { String board ->
                def supported = false
                def supported_boards = ["adrv9361", "adrv9364", "ad9361", "ad9364", "pluto"]
                for(s in supported_boards){
                    if (board.contains(s)){
                        supported = true
                    }
                }
                if(supported && gauntEnv.libad9361_iio_branch != null){
                    try{
                        stage("Test libad9361") {
                            def ip = nebula("update-config -s network-config -f dutip --board-name="+board)
                            run_i('sudo rm -rf libad9361-iio')
                            run_i('git clone -b '+ gauntEnv.libad9361_iio_branch + ' ' + gauntEnv.libad9361_iio_repo, true)
                            dir('libad9361-iio')
                            {
                                sh 'mkdir build'
                                dir('build')
                                {
                                    sh 'cmake -DPYTHON_BINDINGS=ON ..'
                                    sh 'make'
                                    sh 'make install'
                                    sh 'ldconfig'
                                    sh 'URI_AD9361="ip:'+ip+'" ctest -T test --no-compress-output -V'
                                }
                            }
                        }
                    }catch(Exception ex){
                        // log Jira
                        try{
                            description = "LibAD9361Tests Failed: ${ex.getMessage()}"
                        } catch(Exception desc){
                                println('Error updating description.')
                        } finally{
                            logJira([board:board, summary:'libad9361 tests failed.', description:description]) 
                        }
                        unstable("LibAD9361Tests Failed: ${ex.getMessage()}")
                    }finally{
                        dir('libad9361-iio/build'){
                            sh "mv Testing ${board}"
                            xunit([CTest(deleteOutputFiles: true, failIfNotNew: true, pattern: "${board}/**/*.xml", skipNoTestFiles: false, stopProcessingIfError: true)])
                            archiveArtifacts artifacts: "${board}/**/*.xml", followSymlinks: false, allowEmptyArchive: true
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
                def description = ""
                def xmlFile = board+'_HWTestResults.xml'
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
                        cmd = 'IIO_URI="ip:'+ip+'" board="'+board+'" M2K_URI="'+getURIFromSerial(board)+'"'
                        cmd += ' elasticserver='+gauntEnv.elastic_server+' timeout -s KILL '+gauntEnv.matlab_timeout
                        cmd += ' /usr/local/MATLAB/'+gauntEnv.matlab_release+'/bin/matlab -nosplash -nodesktop -nodisplay'
                        cmd += ' -r "run(\'matlab_commands.m\');exit"'
                        statusCode = sh script:cmd, returnStatus:true
                    }catch (Exception ex){
                        // log Jira
                        xmlFile =  sh(returnStdout: true, script: 'ls | grep _*Results.xml').trim()
                        try{
                            description += readFile 'failures.txt'
                        }catch(Exception desc){
                            println('Error updating description.')
                        }finally{
                            logJira([board:board, summary:'MATLAB tests failed.', description: description, attachment:[xmlFile]])  
                        }
                        throw new NominalException(ex.getMessage())
                    }finally{
                            junit testResults: '*.xml', allowEmptyResults: true
                            // archiveArtifacts artifacts: xmlFile, followSymlinks: false, allowEmptyArchive: true
                            // get MATLAB hardware test results for logging
                            if(fileExists(xmlFile)){
                                try{
                                    parseForLogging ('matlab', xmlFile, board)
                                }catch(Exception ex){
                                    println('Parsing MATLAB hardware results failed')
                                    echo getStackTrace(ex)
                                }
                            }
                            // Print test result summary and set stage status depending on test result
                            if (statusCode != 0) {
                                currentBuild.result = 'FAILURE'
                            }
                            switch (statusCode) {
                                case 1:
                                    unstable("MATLAB: Error encountered when running the tests.")
                                    break
                                case 2:
                                    unstable("MATLAB: Some tests failed.")
                                    break
                                case 3:
                                    unstable("MATLAB: Some tests did not run to completion.")
                                    break
                            }
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
                            cmd = 'IIO_URI="ip:'+ip+'" board="'+board+'" M2K_URI="'+getURIFromSerial(board)+'"'
                            cmd += ' elasticserver='+gauntEnv.elastic_server+' timeout -s KILL '+gauntEnv.matlab_timeout
                            cmd += ' /usr/local/MATLAB/'+gauntEnv.matlab_release+'/bin/matlab -nosplash -nodesktop -nodisplay'
                            cmd += ' -r "run(\'matlab_commands.m\');exit"'
                            statusCode = sh script:cmd, returnStatus:true
                        }catch (Exception ex){
                            // log Jira
                            xmlFile =  sh(returnStdout: true, script: 'ls | grep _*Results.xml').trim()
                            try{
                                description += readFile 'failures.txt'
                            }catch(Exception desc){
                                println('Error updating description.')
                            }finally{
                                logJira([board:board, summary:'MATLAB tests failed.', description: description, attachment:[xmlFile]])  
                            }
                            throw new NominalException(ex.getMessage())
                        }finally{
                            junit testResults: '*.xml', allowEmptyResults: true
                            // archiveArtifacts artifacts: xmlFile, followSymlinks: false, allowEmptyArchive: true
                            // get MATLAB hardware test results for logging
                            if(fileExists(xmlFile)){
                                try{
                                    parseForLogging ('matlab', xmlFile, board)
                                }catch(Exception ex){
                                    println('Parsing MATLAB hardware results failed')
                                    echo getStackTrace(ex)
                                }
                            }
                            // Print test result summary and set stage status depending on test result
                            if (statusCode != 0) {
                                currentBuild.result = 'FAILURE'
                            }
                            switch (statusCode) {
                                case 1:
                                    unstable("MATLAB: Error encountered when running the tests.")
                                    break
                                case 2:
                                    unstable("MATLAB: Some tests failed.")
                                    break
                                case 3:
                                    unstable("MATLAB: Some tests did not run to completion.")
                                    break
                            }
                        }
                    }
                }
            }
        }
        break

    case 'KuiperCheck':
            cls = { String board ->
                    println("Checking Kuiper deployment for $board")
                    stage('Kuiper Check'){
                        try{
                            // Download tool
                            run_i(
                                "git clone -b $gauntEnv.kuiper_checker_branch $gauntEnv.kuiper_checker_repo",
                                do_retry=true
                            )
                            dir('kuiper-post-build-checker'){
                                // install kpbc requirements, retry on failure
                                run_i('pip3 install -r requirements.txt', true)
                                // fetch kuiper gen, retry on failure
                                run_i('invoke fetchkuipergen', true)
                                // get board ip
                                def ip = nebula('update-config network-config dutip --board-name='+board)
                                // execute test
                                cmd = "python3 -m pytest -v --html=testhtml/$board" + "_kpbc_report.html" 
                                cmd = cmd + " --junitxml=testxml/$board" + "_kpbc_reports.xml"
                                cmd = cmd + " --ip=$ip -m \"not hardware_check\" --capture=tee-sys"
                                def statusCode = sh script:cmd, returnStatus:true  
                                // generate html report
                                if (fileExists("testhtml/$board" + "_kpbc_report.html")){
                                    publishHTML(target : [
                                        escapeUnderscores: false, 
                                        allowMissing: false, 
                                        alwaysLinkToLastBuild: false, 
                                        keepAll: true, 
                                        reportDir: 'testhtml', 
                                        reportFiles: "$board" + "_kpbc_report.html", 
                                        reportName: board, 
                                        reportTitles: board])
                                }
                                // TODO: parse result for elastic logging
                                // throw exception if pytest failed
                                if ((statusCode != 5) && (statusCode != 0)){
                                    // Ignore error 5 which means no tests were run
                                    throw new NominalException('Kuiper Check Failed')
                                }
                            }
                        }
                        finally{
                            // archive result
                            junit testResults: 'kuiper-post-build-checker/testxml/*.xml', allowEmptyResults: true 
                        }
                    }
                }
            break
    case 'CaptureIIOContext':
        println('Added Capture IIO Context with iio-emu')
        cls = { String board ->
            stage("Install iio-emu") {
                sh 'git clone https://github.com/analogdevicesinc/libtinyiiod.git'
                dir('libtinyiiod')
                {
                    sh 'mkdir build'
                    dir('build')
                    {
                        sh 'cmake -DBUILD_EXAMPLES=OFF ..'
                        sh 'make'
                        sh 'make install'
                        sh 'ldconfig'
                    }
                }
                sh 'git clone -b v0.1.0 https://github.com/analogdevicesinc/iio-emu.git'
                dir('iio-emu')
                {
                    sh 'mkdir build'
                    dir('build')
                    {
                        sh 'cmake -DBUILD_TOOLS=ON ..'
                        sh 'make'
                        sh 'make install'
                        sh 'ldconfig'
                    }
                }
                }
            stage("Capture IIO Context with iio-emu") {
                def ip = nebula('update-config network-config dutip --board-name='+board)
                sh 'xml_gen ip:'+ip+' > "'+board+'.xml"'
                archiveArtifacts artifacts: '*.xml'
            }
        }
        break
    case 'noOSTest':
        cls = { String board ->
            def under_scm = true
            def example = nebula('update-config board-config example --board-name='+board)
            stage('Check JTAG connection'){
                nebula('manager.check-jtag --board-name=' + board + ' --vivado-version=' +gauntEnv.vivado_ver)
            }
            stage('Build NO-OS Project'){
                def pwd = sh(returnStdout: true, script: 'pwd').trim()
                withEnv(['VERBOSE=1', 'BUILD_DIR=' +pwd]){
                    def project = nebula('update-config board-config no-os-project --board-name='+board)
                    def jtag_cable_id = nebula('update-config jtag-config jtag_cable_id --board-name='+board)
                    def files = ['2019.1':'system_top.hdf', '2020.1':'system_top.xsa', '2021.1':'system_top.xsa']
                    sh 'apt-get install libncurses5-dev libncurses5 -y' //remove once docker image is updated
                    try{
                        file = files[gauntEnv.vivado_ver.toString()]
                    }catch(Exception ex){
                        throw new Exception('Vivado version not supported: '+ gauntEnv.vivado_ver) 
                    }
                    try{
                        nebula('dl.bootfiles --board-name=' + board + ' --source-root="' + gauntEnv.nebula_local_fs_source_root + '" --source=' + gauntEnv.bootfile_source
                                +  ' --branch="' + gauntEnv.hdlBranch.toString() +  '" --filetype="noos"')
                    }catch(Exception ex){
                        throw new Exception('Downloader error: '+ ex.getMessage()) 
                    }

                    dir('no-OS'){
                        under_scm = isMultiBranchPipeline()
                        if (under_scm){
                            retry(3) {
                                sleep(5)
                                sh 'git submodule update --recursive --init'
                            }
                        }
                        else {
                            println("Not a multibranch pipeline. Cloning "+gauntEnv.no_os_branch+" branch from "+gauntEnv.no_os_repo)
                            retry(3) {
                                sleep(2)
                                sh 'git clone --recursive -b '+gauntEnv.no_os_branch+' '+gauntEnv.no_os_repo+' .'
                            }
                        }
                    }
                    sh 'cp '+pwd+'/outs/' +file+ ' no-OS/projects/'+ project +'/'
                    dir('no-OS'){
                        if (gauntEnv.vivado_ver == '2020.1'){
                            sh 'git revert 76c709e'
                        }
                        dir('projects/'+ project){
                            def buildfile = readJSON file: 'builds.json'
                            flag = buildfile['xilinx'][example]['flags']
                            if (gauntEnv.vivado_ver == '2020.1' || gauntEnv.vivado_ver == '2021.1' ){
                                sh 'ln /usr/bin/make /usr/bin/gmake'
                            }
                            sh 'source /opt/Xilinx/Vivado/' +gauntEnv.vivado_ver+ '/settings64.sh && make HARDWARE=' +file+ ' '+flag
                            retry(3){
                                sleep(2)
                                sh 'source /opt/Xilinx/Vivado/' +gauntEnv.vivado_ver+ '/settings64.sh && make run' +' JTAG_CABLE_ID='+jtag_cable_id
                            }
                        }
                    }
                }
            }
            switch (example){
                case 'iio':
                    stage('Check Context'){
                        def serial = nebula('update-config uart-config address --board-name='+board)
                        def baudrate = nebula('update-config uart-config baudrate --board-name='+board)
                        try{
                            retry(3){
                                echo '---------------------------'
                                sleep(10);
                                echo "Check context"
                                sh 'iio_info -u serial:' + serial + ',' +gauntEnv.iio_uri_baudrate.toString()
                            }
                        }catch(Exception ex){
                            retry(3){
                                echo '---------------------------'
                                sleep(10);
                                echo "Check context"
                                sh 'iio_info -u serial:' + serial + ',' +baudrate
                            }
                        }
                    }
                    break
                case 'dma_example':
                    // TODO
                default:
                    throw new Exception('Example not yet supported: ' + example)
            }

             
        }
            break
    case 'PowerCycleBoard':
        println('Added Stage Power Cycle Board through PDU')
        cls = { String board ->
            stage('Power Cycle'){
                def pdutype = nebula('update-config pdu-config pdu_type --board-name='+board)
                def outlet = nebula('update-config pdu-config outlet --board-name='+board)
                nebula('pdu.power-cycle -b ' + board + ' -p ' + pdutype + ' -o ' + outlet)
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

private def log_artifacts(){
    // execute to one of available agents
    def agent = gauntEnv.agents[0]
    node(agent){
        stage('Log Artifacts'){
            def command = "telemetry grab-and-log-artifacts"
            command += " --jenkins-server ${JENKINS_URL}"
            command += " --es-server ${gauntEnv.elastic_server}"
            command += " --job-name ${env.JOB_NAME} --job ${env.BUILD_NUMBER}"

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
    def enable_resource_queuing = gauntEnv.enable_resource_queuing
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
        node(agent) {
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
                        stages[k].call(board, ml_variants[ml_variant_index++])
                    else
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
    
    def oneNodeDocker = { agent, num_stages, stages, board, docker_image_name, enable_update_boot_pre_docker_flag, pre_docker_closure, docker_stat, update_container, update_requirements ->
        def k
        def ml_variants = ['rx','tx','rx_tx']
        def ml_variant_index = 0
        node(agent) {
            try {
                if (enable_update_boot_pre_docker_flag)
                    pre_docker_closure.call(board)
                docker.image(docker_image_name).inside(docker_args) {
                    try {
                        stage('Setup Docker') {
                            sh 'apt-get clean'
                            sh 'cd /var/lib/apt && mv lists lists.bak; mkdir -p lists/partial'
                            sh 'cp /default/nebula /etc/default/nebula'
                            sh 'cp /default/pip.conf /etc/pip.conf || true'
                            sh 'cp /default/pydistutils.cfg /root/.pydistutils.cfg || true'
                            sh 'mkdir -p /root/.config/pip && cp /default/pip.conf /root/.config/pip/pip.conf || true'
                            sh 'cp /default/pyadi_test.yaml /etc/default/pyadi_test.yaml || true'
                            def deps = check_update_container_lib(update_container)
                            if (deps.size()>0){
                                setupAgent(deps, true, update_requirements)
                            }
                            // Above cleans up so we need to move to a valid folder
                            sh 'cd /tmp'
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
                                stages[k].call(board, ml_variants[ml_variant_index++])
                            else
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
                    def lock_name = extractLockName(board, agent)
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
                                docker_status,
                                update_container_lib,
                                update_lib_requirements
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
                                docker_status,
                                update_container_lib,
                                update_lib_requirements
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
def isMultiBranchPipeline() {
    isMultiBranch = false
    println("Checking if multibranch pipeline..")
    try
    {
        retry(3){
            checkout scm
            isMultiBranch = true
        }
    }
    catch(all)
    {
        println("Not a multibranch pipeline")
    }
    return isMultiBranch
}

/**
 * Set the value of reference branch for the board recovery stage.
 * @param reference string. Available options: 'SD', 'boot_partition_master', 'boot_partition_release'
 */
def set_recovery_reference(reference) {
    gauntEnv.recovery_ref = reference
}

/**
 * Enable logging issues to Jira. Setting true will update existing Jira issues or create a new issue.
 * @param log_jira Boolean of enable jira logging.
 */
def set_log_jira(log_jira) {
    gauntEnv.log_jira = log_jira
}

/**
 * Set stages where Jira issues should be updated or created.
 * @param log_jira_stages List of stage names
 */
def set_log_jira_stages(log_jira_stages) {
    gauntEnv.log_jira_stages = log_jira_stages
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
 * Creates or updates existing Jira issue for carrier-daughter board
 * Each stage has its own Jira thread for each carrier-daughter board
 * Required key: jiraArgs.summary, other fields have default values or optional
 * attachments is a list of filesnames to upload in the Jira issue
 * Default values:  Jira site: ADI SDG
 *                  project: HTH
 *                  issuetype: Bug
 *                  assignee: JPineda3
 *                  component: KuiperTesting
 */

def logJira(jiraArgs) {
    defaultFields = [site:'sdg-jira',project:'HTH', assignee:'JPineda3', issuetype:'Bug', components:"KuiperTesting", description:"Issue exists in recent build."]
    optionalFields = ['assignee','issuetype','description']
    def key = ''
    // Assign default values if not defined in jiraArgs
    for (field in defaultFields.keySet()){
        if (!jiraArgs.containsKey(field)){
            jiraArgs.put(field,defaultFields."${field}")
        }
    }
    // Append [carier-daugther] to summary
    jiraArgs.board = jiraArgs.board.replaceAll('_', '-')
    try{
        jiraArgs.summary = "["+nebula('update-config board-config carrier --board-name='+jiraArgs.board )+"-"+nebula('update-config board-config daughter --board-name='+jiraArgs.board )+"] ".concat(jiraArgs.summary)
    }catch(Exception summary){
        println('Jira: Cannot append [carier-daugther] to summary.')
    }
    // Include hdl and linux hash if available
    try{
        jiraArgs.description = "{color:#de350b}*[hdl_hash:"+get_elastic_field(jiraArgs.board, 'hdl_hash' , 'NA')+", linux_hash:"+get_elastic_field(jiraArgs.board, 'linux_hash' , 'NA')+"]*{color}\n".concat(jiraArgs.description)
        jiraArgs.description = "["+env.JOB_NAME+'-build-'+env.BUILD_NUMBER+"]\n".concat(jiraArgs.description)
    }catch(Exception desc){
        println('Jira: Cannot include hdl and linux hash to description.')
    }
    echo 'Checking if Jira logging is enabled..'
    try{
        if (gauntEnv.log_jira) {
            echo 'Checking if stage is included in log_jira_stages'
            if  (gauntEnv.log_jira_stages.isEmpty() || !gauntEnv.log_jira_stages.isEmpty() && (env.STAGE_NAME in gauntEnv.log_jira_stages)) {
                println('Jira logging is enabled for '+env.STAGE_NAME+'. Checking if Jira issue with summary '+jiraArgs.summary+' exists..')
                existingIssuesSearch  = jiraJqlSearch jql: "project='${jiraArgs.project}' and summary  ~ '\"${jiraArgs.summary}\"'", site: jiraArgs.site, failOnError: true
                // Comment on existing Jira ticket
                if (existingIssuesSearch.data.total != 0){ 
                    echo 'Updating existing issue..'
                    existingIssue = existingIssuesSearch.data.issues
                    key = existingIssue[0].key
                    issueUpdate = jiraArgs.description
                    comment = [body: issueUpdate]
                    jiraAddComment site: jiraArgs.site, idOrKey: key, input: comment
                }
                // Create new Jira ticket
                else{
                    echo 'Issue does not exist. Creating new Jira issue..'
                    // Required fields
                    issue = [fields: [
                        project: [key: jiraArgs.project],
                        summary: jiraArgs.summary,
                        assignee: [name: jiraArgs.assignee],
                        issuetype: [name: jiraArgs.issuetype],
                        components: [[name:jiraArgs.components]]]]
                    // Optional fields
                    for (field in optionalFields){
                        if (jiraArgs.containsKey(field)){
                            if (field == 'description'){
                                issue.fields.put(field,jiraArgs."${field}")
                            }else{
                                issue.fields.put(field,[name:jiraArgs."${field}"])
                            }
                        }
                    }
                    def newIssue = jiraNewIssue issue: issue, site: jiraArgs.site
                    key = newIssue.data.key
                }
                // Upload attachment if any
                if (jiraArgs.containsKey("attachment") && jiraArgs.attachment != null){ 
                    echo 'Uploading attachments..'
                    for (attachmentFile in jiraArgs.attachment){
                        def attachment = jiraUploadAttachment site: jiraArgs.site, idOrKey: key, file: attachmentFile
                    } 
                }
            }else{
                println('Jira logging is not enabled for '+env.STAGE_NAME+'.')
            }
        }else{
            echo 'Jira logging is disabled for all stages.'
        }
    }catch(Exception jiraError){
        println('Error creating/updating Jira issue.')
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
        def s = gauntEnv.required_hardware.size()
        def b = gauntEnv.boards.size()
        def rh = gauntEnv.required_hardware
        def filtered_board_list = []
        def filtered_agent_list = []

        if (s != 0){
            // if required_hardware is not set, required hardware will be taken from nebula-config
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
            if (!gauntEnv.required_agent.isEmpty()){
                if (computer.name in gauntEnv.required_agent){
                    online_agents.add(computer.name)
                }
            }else{
                online_agents.add(computer.name)
            }
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

def String getURIFromSerial(String board){
    // Utility method to get uri from IIO device serial number
    if (board == 'm2k') {
        serial_no = nebula('update-config board-config serial --board-name='+board)
    }
    else {
        serial_no = nebula('update-config board-config instr-serial --board-name='+board)
    }
    cmd="iio_info -s | grep serial="+serial_no+" | grep -Po \"\\[.*:.*\" | sed 's/.\$//' | cut -c 2-"
    instr_uri = sh(script:cmd, returnStdout: true).trim()
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
        sh 'pip3 uninstall nebula -y || true'
        sh 'pip3 install .'
    }
}

private def install_libiio() {
    if (checkOs() == 'Windows') {
        run_i('git clone -b ' + gauntEnv.libiio_branch + ' ' + gauntEnv.libiio_repo, true)
        dir('libiio')
        {
            bat 'mkdir build'
            bat('build')
            {
                bat 'cmake .. -DPYTHON_BINDINGS=ON -DWITH_SERIAL_BACKEND=ON -DHAVE_DNS_SD=OFF'
                bat 'cmake --build . --config Release --install'
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
        sh 'mkdir build'
        dir('build')
        {
            sh 'cmake .. -DPYTHON_BINDINGS=ON -DWITH_SERIAL_BACKEND=ON -DHAVE_DNS_SD=OFF'
            sh 'make'
            sh 'sudo make install'
            sh 'ldconfig'
            // install python bindings
            dir('bindings/python'){
                sh 'python3 setup.py install'
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
        // sh 'pip3 uninstall telemetry -y || true'
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
        sh 'pip3 install .'
    }
}

private def setup_locale() {
    sh 'sudo apt-get install -y locales'
    sh 'export LC_ALL=en_US.UTF-8 && export LANG=en_US.UTF-8 && export LANGUAGE=en_US.UTF-8 && locale-gen en_US.UTF-8'
}

private def setup_libserialport() {
    sh 'sudo apt-get install -y autoconf automake libtool'
    sh 'git clone https://github.com/sigrokproject/libserialport.git'
    dir('libserialport'){
        sh './autogen.sh'
        sh './configure --prefix=/usr/sp'
        sh 'make'
        sh 'make install'
        sh 'cp -r /usr/sp/lib/* /usr/lib/x86_64-linux-gnu/'
        sh 'cp /usr/sp/include/* /usr/include/'
        sh 'date -r /usr/lib/x86_64-linux-gnu/libserialport.so.0'
    }
}

private def check_update_container_lib(update_container_lib=false) {
    def deps = []
    def default_branch = 'master'
    if (update_container_lib){
        deps = gauntEnv.required_libraries
    }else{
        for(lib in gauntEnv.required_libraries){
            if(gauntEnv[lib+'_branch'] != default_branch){
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
    
    if (fileExists('outs/properties.yaml')){
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
    } else if(fileExists('outs/properties.txt')){
        dir ('outs'){
            def file = readFile 'properties.txt'
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
    if (board.contains("-v")){
        if (board.split("-v")[1] in valid_markers){
            board_name = board.split("-v")[0]
            marker = ' --' + board.split("-v")[1]
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

private def parseForLogging (String stage, String xmlFile, String board) {
    stage_logs = stage + '_logs'
    forLogging = [:]
    forLogging.put(stage_logs,['errors', 'failures', 'skipped', 'tests'])
    println forLogging.keySet()
    forLogging."${stage_logs}".each {
        cmd = 'cat ' + xmlFile + ' | sed -rn \'s/.*' 
        cmd+= it + '="([0-9]+)".*/\\1/p\''
        set_elastic_field(board.replaceAll('_', '-'), stage + '_' + it, sh(returnStdout: true, script: cmd).trim())
    }
}
