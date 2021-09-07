Nebula
========

What is Nebula?
---------------

To aid in development board management and interfacing the Nebula python tool is used.
To know more about this tool, visit: `Nebula`_

.. _Nebula: https://nebula-fpga-dev.readthedocs.io/en/latest/?badge=latest

Using Nebula in Jenkins Shared Library
--------------------------------------

To use the commands available in Nebula pythoon tool, a method *nebula* is added into Jenkins Shared Library. This enables passing the nebula commads that we want to run in order to communicate with the board.

This method is used within the available stages in the stage library and can also be used in your custom stages. An example is shown in how you can leverage this methos in your custom stages.

Example 1 - PowerBoard
^^^^^^^^^^^^^^^^^^^^^^

In this example, we defined first the nebula command that we want to pass to the nebula method.

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

Example 2 - MATLABTest
^^^^^^^^^^^^^^^^^^^^^^

In this example, we use the nebula method to get the ip address of the board that we then later on used in the running the test.

.. code-block:: groovy

    def MATLABTest = {
        String board ->
        stage("Test MATLAB") {
            def ip = nebula('update-config network-config dutip --board-name=' + board)
            sh 'cp -r /root/.matlabro /root/.matlab'
            retry(3) {
                sleep(5)
                checkout scm
                sh 'git submodule update --init'
            }
            try {
                dir('TransceiverToolbox')
                {
                    sh 'mkdir dev'
                    dir('dev')
                    {
                        sh 'wget https://github.com/analogdevicesinc/TransceiverToolbox/releases/download/v20.2.1/AnalogDevicesTransceiverToolbox_v20.2.1.mltbx'
                        sh 'mv *.mltbx a.zip'
                        sh 'unzip a.zip'
                        sh 'mv fsroot/deps ../'
                    }
                }   
                sh 'IIO_URI="ip:' + ip + '" /usr/local/MATLAB/R2020b/bin/matlab -nosplash -nodesktop -nodisplay -r "addpath(genpath(\'test\'));addpath(genpath(\'deps\'));runLTETests(\''+board+'\')"'
        
            } 
            finally {
                junit testResults: '*.xml', allowEmptyResults: true
            }
        }
    }

