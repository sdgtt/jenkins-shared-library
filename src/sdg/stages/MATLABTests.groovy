package sdg.stages
import sdg.Gauntlet
import sdg.stages.IStage
import sdg.NominalException

/**
 * The MATLABTests class implements the IStage interface
 * and provides functionality to run matlab tests on the target board.
 */
class MATLABTests implements IStage {
    /**
     * Returns the name of the stage.
     *
     * @return The name of the stage, which is "MATLABTests".
     */
    String getStageName(){
        return "MATLABTests"
    }

    /**
  
     */
    Closure getCls(){
        return { gauntlet, board ->
            gauntlet.stepExecutor.stage(getStageName()){
                stageSteps(gauntlet, board)
            }
        }
    }
    /**
     * Executes the steps for the LibAD9361Tests stage.
     *
     * @param gauntlet The Gauntlet instance used to execute the stage.
     * @param board The name of the board to be power cycled.
     */
    void stageSteps(Gauntlet gauntlet, String board){
        def logger = gauntlet.logger
        def gauntEnv = gauntlet.gauntEnv
        def steps = gauntlet.stepExecutor

        logger.info("Running ${getStageName()} for ${board}")
        def ip = gauntlet.nebula('update-config network-config dutip --board-name='+board)
        def description = ""
        def xmlFile = board+'_HWTestResults.xml'
        steps.sh('cp -r /root/.matlabro /root/.matlab')
        def result = gauntlet.isMultiBranchPipeline(gauntEnv.matlab_repo)
        result.branch = !result.isMultiBranch ? gauntEnv.matlab_branch : result.branch
        def cloneConfigs = [credentialsId: '', url: gauntEnv.matlab_repo]
        cloneConfigs['refspec'] = result.isMultiBranch ? result.ref : cloneConfigs['refspec']
        steps.checkout([
            $class : 'GitSCM',
            branches : [[name: result.branch]],
            userRemoteConfigs: [cloneConfigs],
            extensions: [
                [$class: 'SubmoduleOption', recursiveSubmodules: true, trackingSubmodules: false]
            ]
        ])
        gauntlet.createMFile()
        
        // Declare statusCode at method scope to ensure it's accessible in try, catch, and finally blocks
        def statusCode = 0
        
        try{
            def cmd = 'chown -R user $(pwd) ; ' 
            cmd += 'sudo -u user IIO_URI="ip:'+ip+'" board="'+board+'" M2K_URI="'+gauntlet.getURIFromSerial(board)+'"'
            cmd += ' elasticserver='+gauntEnv.elastic_server+' timeout -s KILL '+gauntEnv.matlab_timeout
            cmd += ' /usr/local/MATLAB/'+gauntEnv.matlab_release+'/bin/matlab -nosplash -nodesktop -nodisplay'
            cmd += ' -r "run(\'matlab_commands.m\');exit"'
            
            // Check if MATLAB executable exists before trying to run it
            def matlabPath = '/usr/local/MATLAB/'+gauntEnv.matlab_release+'/bin/matlab'
            if (!steps.fileExists(matlabPath)) {
                throw new NominalException("MATLAB executable not found at: " + matlabPath)
            }
            
            statusCode = steps.sh(script:cmd, returnStatus:true)
        }catch (Exception ex){
            xmlFile =  steps.sh(returnStdout: true, script: 'ls | grep _*Results.xml').trim()
            // If we reach here due to sh command failure, assume error status
            if (statusCode == 0) {
                statusCode = 1  // Assume error encountered
            }
            throw new NominalException(ex.getMessage())
        }finally{
            steps.junit testResults: '*.xml', allowEmptyResults: true
            // archiveArtifacts artifacts: xmlFile, followSymlinks: false, allowEmptyArchive: true
            // get MATLAB hardware test results for logging
            if(steps.fileExists(xmlFile)){
                try{
                    gauntlet.parseForLogging ('matlab', xmlFile, board)
                }catch(Exception ex){
                    logger.info('Parsing MATLAB hardware results failed')
                    logger.info(gauntlet.getStackTrace(ex))
                }
            }
            // Print test result summary and set stage status depending on test result
            if (statusCode != 0) {
                // Note: currentBuild access would need to be handled through Jenkins context
                logger.info("MATLAB tests failed with status code: " + statusCode)
            }
            handleTestResult(steps, statusCode)
        }
    }

    /**
     * Handles the test result based on status code
     * @param steps The step executor
     * @param statusCode The status code from MATLAB test execution
     */
    void handleTestResult(steps, statusCode) {
        def intStatusCode = statusCode as Integer
        switch (intStatusCode) {
            case 1:
                steps.unstable("MATLAB: Error encountered when running the tests.")
                break
            case 2:
                steps.unstable("MATLAB: Some tests failed.")
                break
            case 3:
                steps.unstable("MATLAB: Some tests did not run to completion.")
                break
        }
    }
}