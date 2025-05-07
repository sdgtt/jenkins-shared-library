package sdg.stages
import sdg.Gauntlet
import sdg.stages.IStage

/**
 * The CaptureIIOContext class implements the IStage interface and provides
 * functionality to capture the IIO context of a board and log messages at different levels.
 */
class CaptureIIOContext implements IStage {
    /**
     * Returns the name of the stage.
     *
     * @return The name of the stage, which is "CaptureIIOContext".
     */
    String getStageName(){
        return "CaptureIIOContext"
    }

    /**
     * Executes the stage steps, capturing the IIO context of the board and logging messages.
     * @return A closure that takes a Gauntlet instance and a board name as parameters.
     *         The closure executes the stage steps within a stage block.
     *
     * @param gauntlet The Gauntlet instance used to set environment variables and log messages.
     * @param board The board name for which the stage is being executed.
     */
    Closure getCls(){
        return { gauntlet, board ->
            gauntlet.stepExecutor.stage(getStageName()){
                stageSteps(gauntlet, board)
            }
        }
    }
    
    void stageSteps(Gauntlet gauntlet, String board){
        def logger = gauntlet.logger
        def gauntEnv = gauntlet.gauntEnv
        def steps = gauntlet.stepExecutor

        logger.info("Running ${getStageName()} for ${board}")
        logger.info("Installing iio-emu")
        steps.sh 'git clone https://github.com/analogdevicesinc/libtinyiiod.git'
        steps.dir('libtinyiiod')
            {
                steps.sh 'mkdir -p build'
                steps.dir('build')
                {
                    steps.sh 'cmake -DBUILD_EXAMPLES=OFF ..'
                    steps.sh 'make'
                    steps.sh 'make install'
                    steps.sh 'ldconfig'
                }
            }
        steps.sh 'git clone -b v0.1.0 https://github.com/analogdevicesinc/iio-emu.git'
        steps.dir('iio-emu')
        {
            steps.sh 'mkdir -p build'
            steps.dir('build')
            {
                steps.sh 'cmake -DBUILD_TOOLS=ON ..'
                steps.sh 'make'
                steps.sh 'make install'
                steps.sh 'ldconfig'
            }
        }

        logger.info("Capturing IIO context with iio-emu")
        def ip = gauntlet.nebula('update-config network-config dutip --board-name='+board)
        steps.sh 'xml_gen ip:'+ip+' > "'+board+'.xml"'
        steps.archiveArtifacts artifacts: '*.xml'
    }
}