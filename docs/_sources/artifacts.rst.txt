Artifact and Test Data Management
=================================

Artifacts
---------

Artifacts are managed in the following places:

* Artifactory: All branch artifacts get pushed here
* SWDownloads: Only master and releases get pushed here

Test Data
---------

All test results, logs, and other metrics are sent to Elasticsearch database that has been deployed internally.