How to Configure Jenkinsfile
============================

As we know, Jenkinsfile is a text file that defines the Jenkins pipeline. It can be stored in the pipeline itself, in a repository through SCM check out, or in config files. Configuring Jenkinsfile for a pipeline that runs on the test harness is quite tricky since it makes use of the Jenkins Shared Library in which contains all the methods and stages to be used.

This section will discuss how Jenkinsfile is configured based on the methods available in Jenkins Shared Library. The bare minimum requirements for a Jenkinsfile will be discussed first, then some additional methods that will help in creating Jenkinsfile more parameterized.

Jenkinsfile Required Parts
--------------------------

First, we will discuss the bare minimum requirements or parts for the Jenkinfile to create a working pipeline as shown on the code block below. 

.. code-block:: groovy

    lock(label: 'adgt_test_harness_boards'){
        @Library('sdgtt-lib@jsl_updates') _ 
        
        //instantiate constructor method
        def harness = getGauntlet()
    
        //update first the agent with the required deps
        harness.update_agents()
    
        //set docker parameters
        harness.set_enable_docker(true)
        harness.set_docker_args(['Vivado']) 

        //set required hardware
        harness.set_required_hardware(["pluto"]) 
    
        // Set stages (stages are run sequentially on agents)
        harness.add_stage(harness.stage_library("LinuxTests"))
        
        // Go go
        harness.run_stages()
    }

We will go through each part, one by one.

Lock and Library
^^^^^^^^^^^^^^^^

First, notice the step – lock. This step is added to control and ensure the resources that are available in the test harness are only accessed one at a time. So, if ever a job is already running and then you happen to build your job too, your job will be queued, it will run after the running job is done. The label ‘adgt_Test_harness_boards’ contains the list of shared resources.

The library call @Library('sdgtt-lib@adgt-test-harness’) fetches the needed library for the pipeline which is in our case is the Jenkins shared Library – declared and managed in Jenkins as ‘sdgtt-lib’ library. Specifying the branch to be used is also possible by using @ after the library name.

.. code-block:: groovy

    lock(label: 'adgt_test_harness_boards'){
        @Library('sdgtt-lib@jsl_updates') _ 
        //...
    }

Instantiate getGauntlet Constructor Method
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

Since we now secured and have access to the library, the next step is to instantiate the constructor method – getGauntlet. This method calls a map that holds all constants and data members that can be overridden when constructing. This imitates a constructor and defines an instance of a Consul object. All according to API. The simplest way to use this is to use the default values that it holds.

.. code-block:: groovy

    def harness = getGauntlet()

getGauntlet can also accept parameters that override the default values. These parameters are mainly related to the source folders of the files needed, for example, bootPartitionBranch which has the following options: master, release (will automatically use the latest release), specific release version (ex. 2018_R2), or NA (will get files from HDL and Linux folders). For other parameters, check //here.

An example of extended usage of getGauntlet is shown below. This is helpful when you want to test a specific version. On the test side, we make use of this to enable multiconfiguration testing.

.. code-block:: groovy
    
    def hdlBranch = "NA"
    def linuxBranch = "NA"
    def bootPartitionBranch = "2019_r2"
    def firmwareVersion = 'v0.32'
    def bootfile_source = 'artifactory'
    def harness = getGauntlet(hdlBranch, linuxBranch, bootPartitionBranch, firmwareVersion, bootfile_source)

Update Agents Libraries
^^^^^^^^^^^^^^^^^^^^^^^

Next on the list is to update first the agents with the required library dependencies. This is done by simply calling the method update_agents. This is required to ensure that libraries are always up to date.

.. code-block:: groovy

    harness.update_agents()

Set Important Parameters
^^^^^^^^^^^^^^^^^^^^^^^^

There are a lot of parameters that can be configured but the docker parameters and resource queuing parameters are necessarily required to be configured.

.. code-block:: groovy

    //set docker parameters
    harness.set_enable_docker(true)
    harness.set_docker_args(['Vivado'])

    //set resource queuing parameter
    harness.set_enable_resource_queuing(true)

set_enable_docker makes sure that the agent pipeline runs in an isolated environment. set_enable_resource_queuing ensures each of the resources is only accessed one at a time per executor, this is helpful especially with boards that have variants like adrv9002.

Set Required Hardware
^^^^^^^^^^^^^^^^^^^^^

At least one device or hardware is to be set for the agent pipeline to continue. The set_required_hardware method enables to set of what devices are to be added to the pipeline. In the example, ‘pluto’ is the required hardware in which pluto should be also available in the test harness for this to work. 

.. code-block:: groovy

    harness.set_required_hardware(["pluto"])

To add multiple devices, simply separate them with a comma.

.. code-block:: groovy

    harness.set_required_hardware(["pluto", 
                                   "zynq-zc706-adv7511-fmcdaq2",
                                    "zynq-adrv9361-z7035-fmc",
                                    "zynq-zed-adv7511-ad9364-fmcomms4",
                                    "zynq-zed-adv7511-ad9361-fmcomms2-3"])

Set Stages
^^^^^^^^^^

In Jenkins Shared Library, there is a method called stage_library which contains the available stages that can be called and added into the pipeline. Check here to view the available :ref:`library-label`. In the example, we used the “LinuxTests” stage. This stage checks for dmesg error and missing devices.

To add this stage to the pipeline, we use the add_stage method. 

.. code-block:: groovy

    harness.add_stage(harness.stage_library("LinuxTests")) 

The add_stage method has this feature that defines the execution flow behavior of the stage defined in cls. The execution type is provided to the second parameter of the 'add_stage' method: "stopWhenFail"(Default) – stops whole pipeline execution at error or "continueWhenFail" – stops current stage execution at error but proceeds to next. An example is shown below.

.. code-block:: groovy

    harness.add_stage(harness.stage_library("LinuxTests"),"stopWhenFail")
    harness.add_stage(harness.stage_library("PyADITests"),"continueWhenFail")

The added stages are run sequentially on agents.

Run Stages
^^^^^^^^^^

The most important part, run_stages is the main method for starting pipeline once configuration is complete. Once called all agents are queried for attached boards and parallel stages will generate and mapped to relevant agents. 

.. code-block:: groovy

    harness.run_stages()

Jenkinsfile Advanced
--------------------

So basically, the example above would run using the default values. And these default values can be overridden by setting them through other available methods. Here are some of the methods and how to use them in overriding the default values.

Setting Source Repositories and Branches
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

A method set_env enables overwriting default source repositories and branches. This is helpful when you want to test a specific branch or you have your fork of the repository. For the list of parameters that can be overwritten, navigate here: :ref:`parameters`.

An example of using this method is shown below.

.. code-block:: groovy

    harness.set_env('nebula_repo','https://github.com/sdgtt/nebula.git')
    harness.set_env('nebula_branch','dev')
    harness.set_env('libiio_branch','v0.21')
    harness.set_env('telemetry_repo','https://github.com/sdgtt/telemetry.git')
    harness.set_env('telemetry_branch','master')

Custom Stages
^^^^^^^^^^^^^^^^^^

In the event of the test, you want to perform is not available on the existing stage library cases, you can add a custom stage. This custom stage can then be added to the agent pipeline by calling the add_stage method. A simple example of adding a custom stage is shown below.  

.. code-block:: groovy

    // Custom stage
    def mytest = {
        stage("Example Stage") {    
            sh 'echo "Run my custom closure"'
            sh 'echo "pew pew"'
        }
    }

    harness.add_stage(mytest)

To access board information we can pass the board into the stage and make use of nebula to communicate with the board as shown in the example below.

.. code-block:: groovy

    def PowerBoard = { String board ->
		try {
			stage("Power Board"){
			    cmd = "pdu.power-board -b ${board}"
                harness.nebula(cmd, true, true, true)
                sleep 60
			}
		}catch(Exception ex) {
			throw new Exception("Task failed. Reason ${ex.getMessage()}")
		}
	}

    harness.add_stage(PowerBoard)

Example Jenkinfile
------------------

.. code-block:: groovy

    lock(label: 'adgt_test_harness_boards'){
        @Library('sdgtt-lib@jsl_updates') _ 
        def hdlBranch = "NA"
        def linuxBranch = "NA"
        def bootPartitionBranch = "2019_r2"
        def firmwareVersion = 'v0.32'
        def bootfile_source = 'artifactory' 
        def harness = getGauntlet(hdlBranch, linuxBranch, bootPartitionBranch, firmwareVersion, bootfile_source)
    
        //udpate repos
        harness.set_env('nebula_repo','https://github.com/sdgtt/nebula.git')
        harness.set_env('nebula_branch','dev')
        harness.set_env('libiio_branch','v0.21')
        harness.set_env('telemetry_repo','https://github.com/kimpaller/telemetry.git')
        harness.set_env('telemetry_branch','master')
    
        //update first the agent with the required deps
        harness.update_agents()
    
        //set other test parameters
        harness.set_nebula_debug(true)
        harness.set_enable_docker(true)
        harness.set_send_telemetry(true)
        harness.set_enable_resource_queuing(true)
        harness.set_required_hardware(["zynq-zed-adv7511-adrv9002-vcmos", 
                                        "zynq-zed-adv7511-adrv9002-rx2tx2-vcmos",
                                        "pluto",
                                        "zynq-zc706-adv7511-fmcdaq2",
                                        "zynq-adrv9361-z7035-fmc"])
        
        harness.set_docker_args(['Vivado']) 
        harness.set_nebula_local_fs_source_root("artifactory.analog.com")
    
    
        // Set stages (stages are run sequentially on agents)
        harness.add_stage(harness.stage_library("UpdateBOOTFiles"), 'stopWhenFail',
                            harness.stage_library("RecoverBoard"))
       
        // Test stage
        harness.add_stage(harness.stage_library("LinuxTests"),'continueWhenFail')
        harness.add_stage(harness.stage_library('PyADITests'),'continueWhenFail')
        harness.add_stage(harness.stage_library('LibAD9361Tests'),'continueWhenFail')
        harness.add_stage(harness.stage_library('SendResults'),'continueWhenFail')
    
        // // Go go
        harness.run_stages()
    }

