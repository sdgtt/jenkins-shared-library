package sdg.stages

class SimplyPrint{
    // Sample Stage Class
    def StageName = "SimplyPrint"
    def doSomething(script, board){
        script.stage(StageName){
            script.println("Running from ${stageName} for ${board}")
            script.stage("Substage"){
                script.println("Another stage")
            }
        }
    }
    def getCls(){
        return { script, board ->
            doSomething(script, board)
        }
    }
}
