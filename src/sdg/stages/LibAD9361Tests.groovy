package sdg.stages
import sdg.Gauntlet
import sdg.stages.IStage

/**
 * The LibAD9361Tests class implements the IStage interface
 * and provides functionality to run libad9361 tests on the target board.
 */
class LibAD9361Tests implements IStage {
    /**
     * Returns the name of the stage.
     *
     * @return The name of the stage, which is "LibAD9361Tests".
     */
    String getStageName(){
        return "LibAD9361Tests"
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
        def supported = false
        def supported_boards = ["adrv9361", "adrv9364", "ad9361", "ad9364", "pluto"]
        for(s in supported_boards){
            if (board.contains(s)){
                supported = true
            }
        }
        if(supported && gauntEnv.libad9361_iio_branch != null){
            try{
                def ip = gauntlet.nebula("update-config -s network-config -f dutip --board-name="+board)
                gauntlet.run_i('sudo rm -rf libad9361-iio')
                gauntlet.run_i('git clone -b '+ gauntEnv.libad9361_iio_branch + ' ' + gauntEnv.libad9361_iio_repo, true)
                steps.dir('libad9361-iio')
                {
                    steps.sh('mkdir -p build')
                    steps.dir('build')
                    {
                        steps.sh('cmake -DPYTHON_BINDINGS=ON ..')
                        steps.sh('make')
                        steps.sh('make install')
                        steps.sh('ldconfig')
                        steps.sh('URI_AD9361="ip:'+ip+'" ctest -T test --no-compress-output -V')
                    }     
                }
            }catch(Exception ex){
                steps.unstable("LibAD9361Tests Failed: ${ex.getMessage()}")
            }finally{
                steps.dir('libad9361-iio/build'){
                    steps.sh("mv Testing ${board}")
                    steps.xunit([CTest(deleteOutputFiles: true, failIfNotNew: true, pattern: "${board}/**/*.xml", skipNoTestFiles: false, stopProcessingIfError: true)])
                    steps.archiveArtifacts artifacts: "${board}/**/*.xml", followSymlinks: false, allowEmptyArchive: true
                }
            }
        }else{
            logger.info("LibAD9361Tests: Skipping board: "+board)
        }
    }
}