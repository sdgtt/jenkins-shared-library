package sdg.stages
import sdg.Gauntlet
import sdg.stages.IStage

/**
 * The SimplyPrint class implements the IStage interface and provides
 * functionality to print messages at different log levels.
 * This class is used to demonstrate the new code structure
 * and will likely be a pattern to all classes that implements the JSL common stages.
 */
class SimplyPrint implements IStage {

    /**
     * Returns the name of the stage.
     *
     * @return The name of the stage, which is "SimplyPrint".
     */
    String getStageName(){
        return "SimplyPrint"
    }

    /**
     * Executes the stage steps, setting the debug level and logging messages
     * at different levels.
     *
     * @param gauntlet The Gauntlet instance used to set environment variables and log messages.
     * @param board The board name for which the stage is being executed.
     */
    void stageSteps(Gauntlet gauntlet, String board){
        gauntlet.set_env("debug_level",2)
        gauntlet.logger.info("Running from ${getStageName()} for ${board}")
        gauntlet.logger.warning("Running from ${getStageName()} for ${board}")
        gauntlet.logger.error("Running from ${getStageName()} for ${board}")
    }

    /**
     * Returns a closure that sets the debug level, logs an info message, and
     * executes the stage steps within a stage block.
     *
     * @return A closure that takes a Gauntlet instance and a board name as parameters.
     */
    Closure getCls(){
        return { gauntlet, board ->
            gauntlet.set_env("debug_level",3)
            gauntlet.logger.info("Running from ${getStageName()} for ${board}")
            gauntlet.stepExecutor.stage(getStageName()){
                stageSteps(gauntlet, board)
            }
        }
    }
}
