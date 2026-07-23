package sdg.stages

import sdg.Gauntlet

interface IStage {

    String getStageName()
    Closure getCls()
}