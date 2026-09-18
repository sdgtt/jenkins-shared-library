package sdg.stages
import sdg.Gauntlet
import sdg.stages.IStage

/**
 * The PowerCycleBoard class implements the IStage interface and provides
 * functionality to power cycle a board and log messages at different levels.
 */
class PowerCycleBoard implements IStage {
    /**
     * Returns the name of the stage.
     *
     * @return The name of the stage, which is "PowerCycleBoard".
     */
    String getStageName(){
        return "PowerCycleBoard"
    }

    /**
     * Executes the stage steps, power cycling the board and logging messages.
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
    /**
     * Executes the steps for the PowerCycleBoard stage.
     *
     * @param gauntlet The Gauntlet instance used to execute the stage.
     * @param board The name of the board to be power cycled.
     */
    void stageSteps(Gauntlet gauntlet, String board){
        def logger = gauntlet.logger
        def gauntEnv = gauntlet.gauntEnv
        def steps = gauntlet.stepExecutor

        logger.info("Running ${getStageName()} for ${board}")
        def pdutype = gauntlet.nebula('update-config pdu-config pdu_type --board-name='+board)
        def outlet = gauntlet.nebula('update-config pdu-config outlet --board-name='+board)
        gauntlet.nebula('pdu.power-cycle -b ' + board + ' -p ' + pdutype + ' -o ' + outlet)
        logger.info("Power cycle done for ${board}")
    }
}