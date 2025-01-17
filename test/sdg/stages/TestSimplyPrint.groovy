package sdg.stages

import spock.lang.Specification
import sdg.Gauntlet
import sdg.Logger
import sdg.IStepExecutor
import sdg.ioc.*
import groovy.lang.GroovyShell

class test_SimplyPrint extends Specification {

    def shell
    def getGauntEnv
    def gauntlet
    IStepExecutor steps
    IContext context

    def setup() {
        // mock the context
        shell = new GroovyShell()
        getGauntEnv = shell.parse(new File('vars/getGauntEnv.groovy'))
        
        steps =  Mock(IStepExecutor.class);
        context = Mock(IContext.class);
        context.getStepExecutor() >> steps
        context.isDefault() >> false
        context.getStepExecutor().getGauntEnv(_,_,_,_,_) >> getGauntEnv.call(_,_,_,_,_)
        ContextRegistry.registerContext(context)

        gauntlet = new Gauntlet()
        gauntlet.construct("NA","NA","NA","NA","NA")
    }

    def "test getStageName"() {
        given:
        SimplyPrint simplyPrint = new SimplyPrint()

        expect:
        simplyPrint.getStageName() == "SimplyPrint"
    }

    def "test stageSteps"() {
        given:
        SimplyPrint simplyPrint = new SimplyPrint()
        String board = "pluto"

        when:
        simplyPrint.stageSteps(gauntlet, board)

        then:
        0 * steps.echo('[INFO] Running from SimplyPrint for pluto')
        1 * steps.echo('[WARNING] Running from SimplyPrint for pluto')
        1 * steps.echo('[ERROR] Running from SimplyPrint for pluto')

        expect:
        gauntlet.get_env("debug_level") == 2
    }

    def "test getCls"() {
        given:
        SimplyPrint simplyPrint = new SimplyPrint()
        String board = "pluto"
        def closure = simplyPrint.getCls()

        when:
        closure.call(gauntlet, board)

        then:
        1 * steps.echo('[INFO] Running from SimplyPrint for pluto')
        1 * steps.stage('SimplyPrint', _)

        expect:
        gauntlet.get_env("debug_level") == 3
    }
}