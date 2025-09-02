package sdg.stages
import sdg.Gauntlet
import sdg.stages.IStage
import sdg.NominalException

/**
 * The KuiperCheck class implements the IStage interface
 * 
 */
class KuiperCheck implements IStage {
    /**
     * Returns the name of the stage.
     *
     * @return The name of the stage, which is "KuiperCheck".
     */
    String getStageName(){
        return "KuiperCheck"
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
        try{
            // Download tool
            gauntlet.run_i(
                "git clone -b $gauntEnv.kuiper_checker_branch $gauntEnv.kuiper_checker_repo"
            )
            steps.dir('kuiper-post-build-checker'){
                // install kpbc requirements, retry on failure
                gauntlet.run_i('pip3 install -r requirements.txt', true)
                // fetch kuiper gen, retry on failure
                gauntlet.run_i('invoke fetchkuipergen', true)
                // get board ip
                def ip = gauntlet.nebula('update-config network-config dutip --board-name='+board)
                // execute test
                def cmd = "python3 -m pytest -v --html=testhtml/$board" + "_kpbc_report.html" 
                cmd = cmd + " --junitxml=testxml/$board" + "_kpbc_reports.xml"
                cmd = cmd + " --ip=$ip -m \"not hardware_check\" --capture=tee-sys"
                def statusCode = steps.sh(script:cmd, returnStatus:true)
                // generate html report
                if (steps.fileExists("testhtml/$board" + "_kpbc_report.html")){
                    steps.publishHTML(target : [
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
            steps.junit testResults: 'kuiper-post-build-checker/testxml/*.xml', allowEmptyResults: true 
        }
    }
}