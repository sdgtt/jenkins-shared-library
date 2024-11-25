package sdg.stages

class UpdateBOOTFiles{
    // Sample Stage Class
    def StageName = "UpdateBOOTFiles"
    def doSomething(script, board){
        script.stage(StageName){
            script.println("Running from ${stageName} for ${board}")
        }
    }
    def getCls(){
        return { script, board ->
            doSomething(script, board)
        }
    }
}