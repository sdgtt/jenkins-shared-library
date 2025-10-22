.. _library-label:

Pipeline Stage Library
======================

This section discusses the available stages that can be called and added to the pipeline.

UpdateBOOTFiles
---------------

This stage downloads the needed files and then proceeds to update the files on the device’s SD card. After updating, the device reboots. For pluto and m2k, this stage downloads their firmware and then updates the device’s firmware.

.. uml:: updatebootfiles_workflow.pu

RecoverBoard
------------

This stage enables users to recover boards when they can no longer be accessed. Reference files are first downloaded, then the board is recovered using the recovery device manager function of Nebula.

.. uml:: recoverboard_workflow.pu

SendResults
-----------

This stage sends the collected results from all stages to the elastic server which will then be processed for easy viewing.

.. uml:: sendresults_workflow.pu

Test Stages
-----------

There are also available test stages defined in the stage library.

LinuxTests
^^^^^^^^^^

This stage checks for dmesg errors, checks iio devices, and runs diagnostics on boards.

.. uml:: linuxtests_workflow.pu

PyADITests
^^^^^^^^^^

This stage runs the pyadi-iio test on the target board.

.. uml:: pyaditests_workflow.pu

LibAD9361Test
^^^^^^^^^^^^^

This stage runs the LibAD9361 tests available on the repository.

.. uml:: libad9361tests_workflow.pu

MATLABTests
^^^^^^^^^^^

This stage runs the MATLAB hardware test runner for the target boards.

.. uml:: matlabtests_workflow.pu