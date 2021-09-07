.. _library-label:

Pipeline Stage Library
======================

This section discusses the available stages that can be called and added to the pipeline.

UpdateBOOTFiles
---------------

This stage downloads the needed files and then proceeds to update the files on the device’s SD card. After updating, the device reboots. For pluto and m2k, this stage downloads their firmware and then updates the device’s firmware.

.. collapse:: UpdateBOOTFiles Stage
  
  .. code-block:: groovy

    case 'UpdateBOOTFiles':
            println('Added Stage UpdateBOOTFiles')
            cls = { String board ->
                try {
                stage('Update BOOT Files') {
                    println("Board name passed: "+board)
                    println(gauntEnv.branches.toString())
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

|

RecoverBoard
------------

This stage enables users to recover boards when they can no longer be accessed. Reference files are first downloaded, then the board is recovered using the recovery device manager function of Nebula.

.. collapse:: RecoverBoard Stage
  
  .. code-block:: groovy

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

|

SendResults
-----------

This stage sends the collected results from all stages to the elastic server which will then be processed for easy viewing.

.. collapse:: SendResults Stage
  
  .. code-block:: groovy

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

|

Test Stages
-----------

There are also available test stages defined in the stage library.

LinuxTests
^^^^^^^^^^

This stage checks for dmesg errors, checks iio devices, and runs diagnostics on boards.

.. collapse:: LinuxTests Stage
  
  .. code-block:: groovy

    case 'LinuxTests':
            println('Added Stage LinuxTests')
            cls = { String board ->
                stage('Linux Tests') {
                    def failed_test = ''
                    def drivers_count = 0
                    def missing_drivers = 0
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
                            missing_drivers = missing_devs.size()
                            writeFile(file: board+'_missing_devs.log', text: missing_devs.join(","))
                            set_elastic_field(board, 'drivers_missing', missing_drivers.toString())
                        }
                        // get drivers enumerated
                        println(nebula('update-config driver-config iio_device_names -b '+board, false, true, false))
                        
                        try{
                            if (!gauntEnv.firmware_boards.contains(board))
                                nebula("net.run-diagnostics --ip='"+ip+"' --board-name="+board, true, true, true)
                                archiveArtifacts artifacts: '*_diag_report.tar.bz2', followSymlinks: false, allowEmptyArchive: true
                        }catch(Exception ex) {
                            failed_test = failed_test + " [diagnostics failed: ${ex.getMessage()}]"
                        }

                        if(failed_test && !failed_test.allWhitespace){
                            throw new Exception("Linux Tests Failed: ${failed_test}")
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

|

PyADITests
^^^^^^^^^^

This stage runs the pyadi-iio test on the target board.

.. collapse:: PyADITests Stage
  
  .. code-block:: groovy

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

|

LibAD9361Test
^^^^^^^^^^^^^

This stage runs the LibAD9361 tests available on the repository.

.. collapse:: LibAD9361Tests Stage
  
  .. code-block:: groovy

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

|

MATLABTests
^^^^^^^^^^^

This stage runs the MATLAB hardware test runner for the target boards.

.. collapse:: MATLABTests Stage
  
  .. code-block:: groovy

    case 'MATLABTests':
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
