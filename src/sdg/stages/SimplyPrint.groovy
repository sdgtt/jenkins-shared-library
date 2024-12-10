package sdg.stages
import sdg.Gauntlet
import sdg.stages.IStage

class SimplyPrint implements IStage {
    // Sample Stage Class
    String getStageName(){
        return "SimplyPrint"
    }

    void stageSteps(Gauntlet gauntlet, String board){
        gauntlet.stepExecutor.println("2 Running from ${getStageName()} for ${board}")
        gauntlet.set_env("debug_level",3)
    }

    Closure getCls(){
        return { gauntlet, board ->
            gauntlet.stepExecutor.println("1 Running from ${getStageName()} for ${board}")
            gauntlet.stepExecutor.stage(getStageName()){
                stageSteps(gauntlet, board)
            }
        }
    }
}
