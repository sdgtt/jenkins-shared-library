.. _all-methods:

*******
Methods
*******

All the method availble in Jenkis Shared Library are listed here.

Constructor Method
==================

getGauntlet
-----------

Calls a map that holds all constants and data members that can be overridden when constructing. 
Imitates a constructor and defines an instance of Consul object. All according to API.

parameters (*optional*):
    * *hdlBranch* - String of name of hdl branch to use for bootfile source, set to 'NA' if bootPartitionBranch is to be used. Options: 'master', 'release', 'NA'. Default: 'NA'
    * *linuxBranch*- String of name of linux branch to use for bootfile source, set to 'NA' if bootPartitionBranch is to be used. Options: 'master', 'release', 'NA'. Default: 'NA'
    * *bootPartitionBranch* - String of name of boot partition branch to use for bootfile source, set to 'NA' if hdl and linux is to be used: Options: 'master', 'release', 'NA'. Default: 'release'
    * *firmwareVersion* - String of name of firmware version branch to use for pluto and m2k. Default: ''
    * *bootfile_source* - String location of bootfiles. Options: sftp, artifactory, http, local. Default: 'artifactory'

return:
    constructed object

Sample usage:

.. code-block:: groovy

    harness = getGauntlet()

Agent-Specific Methods
======================

print_agents
------------

Prints list of all online agents connected to the harness.
No parameter needed.

Sample usage:

.. code-block:: groovy

    harness = getGauntlet()
    harness.print_agents()

run_agents
----------

Main method for starting pipeline once configuration is complete.
Once called all agents are queried for attached boards and parallel stages will generated and mapped to relevant agents

Sample usage:

.. code-block:: groovy

    harness = getGauntlet()
    harness.run_agents()

update_agents
-------------

Updates the dependencies required on all agents. This is called at the beginning of the pipeline.
No parameters needed.

Sample usage:

.. code-block:: groovy

    harness = getGauntlet()
    harness.update_agents()

Add Stages Methods
==================

stage_library
-------------

Consists the available stages that can be called. Returns a closure of the requested stage.

Parameters:
    * *stage_name* - String name of stage

return:
    Closure of stage requested

Sample usage:

.. code-block:: groovy

    harness = getGauntlet()
    harness.add_stage(harness.stage_library("LinuxTests"),"stopWhenFail")

add_stage
---------

Add stage to agent pipeline.

parameters:
    * *cls* - Closure of stage(s). Should contain at least one stage closure.
    * *option* - Defines the execution flow behavior of the stage defined in cls. Execution type is provided to the second parameter of the 'add_stage' method:
            "stopWhenFail"(Default) - stops whole pipeline execution at error; set build status to 'FAILURE'
            "continueWhenFail" - stops current stage execution at error but proceeds to next; set build status to 'UNSTABLE'
    * *delegatedCls* - The stage closure that will be executed when cls fails for option 'stopWhenFail'

Sample usage

.. code-block:: groovy

    harness = getGauntlet()
    
    // Custom test stage
    def mytest = {
        stage("Example Stage") {    
            sh 'echo "Run my custom closure"'
            sh 'echo "pew pew"'
        }
    }

    harness.add_stage(mytest)
    harness.add_stage(harness.stage_library("LinuxTests"),"stopWhenFail")

Set/Get Parameters Methods
==========================

Generic
-------

.. _parameters:

set_env
^^^^^^^

Can override all constants and data members that the map gauntEnv holds. Env setter method.

parameters:
    * *param* - String parameter name
    * *value* - value to set for the parameter

Some data members and defaults:

.. code-block:: groovy

    pytest_libiio_repo: 'https://github.com/tfcollins/pytest-libiio.git',
    pytest_libiio_branch: 'master',
    pyadi_iio_repo: 'https://github.com/analogdevicesinc/pyadi-iio.git',
    pyadi_iio_branch: 'master',
    libad9361_iio_repo: 'https://github.com/analogdevicesinc/libad9361-iio.git',
    libad9361_iio_branch: 'master',
    nebula_repo: 'https://github.com/tfcollins/nebula.git',
    nebula_branch: 'master',
    libiio_repo: 'https://github.com/analogdevicesinc/libiio.git',
    libiio_branch: 'master',
    telemetry_repo: 'https://github.com/sdgtt/telemetry.git',
    telemetry_branch: 'master',
    matlab_release: 'R2021a',
    matlab_repo: 'https://github.com/analogdevicesinc/TransceiverToolbox.git',
    matlab_branch: 'master',
    nebula_config_repo: 'https://github.com/sdgtt/nebula-config.git',
    nebula_config_branch: 'master',

Sample usage:

.. code-block:: groovy

    harness = getGauntlet()

    harness.set_env('nebula_repo','https://github.com/sdgtt/nebula.git')
    harness.set_env('nebula_branch','dev')
    harness.set_env('libiio_branch','v0.21')
    harness.set_env('telemetry_repo','https://github.com/sdgtt/telemetry.git')
    harness.set_env('telemetry_branch','master')

get_env
^^^^^^^

Gets current value of parameters. Env getter method.

parameters:
    * *param* - String parameter name

return:
    value of the parameter

Sample usage:

.. code-block:: groovy

    harness = getGauntlet()

    harness.get_env('nebula_repo')

Docker-related
--------------

set_enable_docker
^^^^^^^^^^^^^^^^^

Enable use of docker at agent during jobs phases.
    
parameter:
    * *enable_docker* - boolean True will enable use of docker

Sample usage:

.. code-block:: groovy

    harness = getGauntlet()
    harness.set_enable_docker(True)

set_docker_args
^^^^^^^^^^^^^^^

Set docker arguments passed to docker container at runtime. Used when set_enable_docker is True.

parameter:
    * *docker_args* - List of strings of arguments, example: Matlab, Vivado

Sample usage:

.. code-block:: groovy

    harness = getGauntlet()
    harness.set_enable_docker(True)
    harness.set_docker_args(['Matlab'])

set_docker_host_mode
^^^^^^^^^^^^^^^^^^^^

Enable use of docker host mode. Used when set_enable_docker is True.

parameter:
    * *docker_host_mode* boolean True will enable use of docker host mode

Sample usage:

.. code-block:: groovy

    harness = getGauntlet()
    harness.set_enable_docker(True)
    harness.set_docker_host_mode(True)

Hardware Resources
------------------

set_required_hardware
^^^^^^^^^^^^^^^^^^^^^

Set list of required devices for test.

parameter:
    * *board_names* - List of strings of names of boards. Strings must be associated with a board configuration name. For example: zynq-zc702-adv7511-ad9361-fmcomms2-3

Sample usage:

.. code-block:: groovy

    harness = getGauntlet()
    harness.set_required_hardware(["zynq-zed-adv7511-ad9361-fmcomms2-3", "pluto"])

IIO URI
-------

set_iio_uri_source
^^^^^^^^^^^^^^^^^^

Set URI source. Supported are ip or serial.

parameter:
    * *iio_uri_source* - String of URI source. Options: ip or serial

Sample usage:

.. code-block:: groovy

    harness = getGauntlet()
    harness.set_iio_uri_source('ip')

set_iio_uri_baudrate
^^^^^^^^^^^^^^^^^^^^

Set URI serial baudrate. Only applicable when iio_uri_source is serial.

parameter:
    * *iio_uri_source* - Integer of URI baudrate

Sample usage:

.. code-block:: groovy

    harness = getGauntlet()
    harness.set_iio_uri_source('serial')
    harness.set_iio_uri_baudrate(115200)

Nebula-related
--------------

nebula
^^^^^^

Use Nebula python tool to communicate with the board.

parameter:
    * *cmd* -  nebula command
    * *full* - default Boolean false 
    * *show_log* - Boolean true enables debug mode
    * *report_error* - Boolean true enables reporting of errors

Sample usage:

.. code-block:: groovy
  
    cmd = "pdu.power-board -b ${board}"
    harness.nebula(cmd, true, true, true)

set_nebula_debug
^^^^^^^^^^^^^^^^

Set nebula to debug mode. Setting true will add ``show-log`` to nebula commands
    
parameter:
    * *nebula_debug* Boolean of debug mode

Sample usage:

.. code-block:: groovy

    harness = getGauntlet()
    harness.set_nebula_debug(True)

set_nebula_local_fs_source_root
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

Set nebula downloader source_path.

parameter:
    * *nebula_local_fs_source_root* - String of path. Options: 'artifactory.analog.com', 'var/lib/tftpboot' (default)

Sample usage:

.. code-block:: groovy

    harness = getGauntlet()
    harness.set_nebula_local_fs_source_root("artifactory.analog.com")

Results
-------

set_elastic_server
^^^^^^^^^^^^^^^^^^

Set elastic server address. Setting will use a non-default elastic search server.
   
parameter:
    * *elastic_server* - String of server IP

Sample usage:

.. code-block:: groovy

    harness = getGauntlet()
    harness.set_elastic_server('192.168.2.1')

set_send_telemetry
^^^^^^^^^^^^^^^^^^

Enable sending results to elastic server using telemetry.

parameter:
    * *send_results* -  Boolean True will enable sending of results

Sample usage:

.. code-block:: groovy

    harness = getGauntlet()
    harness.set_send_telemetry(True)

Other Parameters
----------------

set_enable_update_boot_pre_docker
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

Enable update boot to be run before docker is launched.
   
parameter:
   * *enable_update_boot_pre_docker* - boolean True will run update boot stage before docker is launch

Sample usage:

.. code-block:: groovy

    harness = getGauntlet()
    harness.set_enable_update_boot_pre_docker(True)

set_job_trigger
^^^^^^^^^^^^^^^

Set the job_trigger variable used in identifying what triggered the execution of the pipeline.

parameter:
    * *trigger* - string, set to manual(default) if manually triggered or auto:<jenkins project name>:<jenkins build number> for auto triggered builds

Sample usage:

.. code-block:: groovy

    def jenkins_job_trigger = "$(trigger)" //parameterized
    harness = getGauntlet()
    harness.set_job_trigger(jenkins_job_trigger)

set_max_retry
^^^^^^^^^^^^^

Set the maximum of retries used in retrying some sh/bat steps.
    
parameter:
    * *max_retry* - integer number of retries

Sample usage:

.. code-block:: groovy

    harness = getGauntlet()
    harness.set_max_retry(3)

set_update_nebula_config
^^^^^^^^^^^^^^^^^^^^^^^^

Enables updating of nebula-config used by nebula

parameter:
    *enable* - Boolean True(default) updates nebula-config of agent, or set to false otherwise

Sample usage:

.. code-block:: groovy

    harness = getGauntlet()
    harness.set_update_nebula_config(False)