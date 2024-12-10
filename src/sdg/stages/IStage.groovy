package sdg.stages

import sdg.Gauntlet

interface IStage {

    String getStageName()
    void stageSteps(Gauntlet gaunlet, String board)
    Closure getCls()
}