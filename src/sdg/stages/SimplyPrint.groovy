package sdg.stages
import sdg.Gauntlet
import sdg.stages.IStage

class SimplyPrint implements IStage {
    // Sample Stage Class
    String getStageName(){
        return "SimplyPrint"
    }

    void stageSteps(Gauntlet gauntlet, String board){
        gauntlet.set_env("debug_level",2)
        gauntlet.logger.info("Running from ${getStageName()} for ${board}")
        gauntlet.logger.warning("Running from ${getStageName()} for ${board}")
        gauntlet.logger.error("Running from ${getStageName()} for ${board}")
    }

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
