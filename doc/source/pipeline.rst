Gauntlet Hardware Pipeline
==========================

The main purpose of this shared library is to provide a standardize pipeline for software project that want to run code against hardware targets. The figure below shows an example pipeline that is generated using the Jenkinsfile below it.


.. graphviz:: pipeline_ex.dot

These pipelines have 3 main phases. Starting from the left side of the pipeline, phase 1 is the first 3 horizontal stages. In the first stage each agent, tools required are updated. Then for the next stage, each agent is queried to determined available hardware. This information is returned to the master node and the necessary downstream stages are determined. This will occur in all pipeline configuration using the **Gaunlet** class. The generated downstream stages will be based on how the **harness** object is configured and each of these downstream stages are run in a docker container, thus the Setup Docker stage which is the start of phase 2.

In the example Jenkinsfile below which also what the figure above reflects, a stage is added from the :ref:`library-label`. Added stages are run in the order in which they are added. Each of these stages will be run for each target board configuration. Defining the board or hardware setup desired, the *set_required_hardware* method should be used.

The final phase is for post processing, here logs and artifacts are gathered for collection. These will typically be saved to artifactory or to a logging server for analysis.

The available flags are documented for reference in the Jenkinsfile below. See :ref:`all-methods` section for explicit syntax and all available methods.

Example Jenkinsfile
-------------------

.. code:: groovy

    lock(label: 'adgt_test_harness_boards'){
        @Library('sdgtt-lib@jsl_updates') _ 
        
        //instantiate constructor method
        def harness = getGauntlet()
    
        //update first the agent with the required deps
        harness.update_agents()
    
        //set docker parameters
        harness.set_enable_docker(true)
        harness.set_docker_args(['Vivado']) 

        //set resource queuing parameter
        harness.set_enable_resource_queuing(true)
        
        //set required hardware
        harness.set_required_hardware(["pluto",
                                    "zynq-zed-adv7511-fmcomms2-3",
                                    "zynq-zc706-adv7511-fmcdaq2"]) 
    
        // Set stages (stages are run sequentially on agents)
        harness.add_stage(harness.stage_library("LinuxTests"))
        
        // Go go
        harness.run_stages()
    }
        

