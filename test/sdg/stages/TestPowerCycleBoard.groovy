package sdg.stages

import spock.lang.Specification
import sdg.Gauntlet
import sdg.Logger
import sdg.IStepExecutor
import sdg.ioc.*
import groovy.lang.GroovyShell

class TestPowerCycleBoard extends Specification {

    def shell
    def getGauntEnv

    IStepExecutor steps
    IContext context

    def setup() {
        // mock the context
        shell = new GroovyShell()
        getGauntEnv = shell.parse(new File('vars/getGauntEnv.groovy'))
        
        steps =  Mock(IStepExecutor.class)
        context = Mock(IContext.class)
    }

    def "test getStageName"() {
        given:
        def PowerCycleBoard = new PowerCycleBoard()

        expect:
        PowerCycleBoard.getStageName() == "PowerCycleBoard"
    }

    def "test getCls"() {
        given:
        def PowerCycleBoard = new PowerCycleBoard()
        String board = "zynq-zc702-adv7511-ad9361-fmcomms2-3"

        when:
        def closure = PowerCycleBoard.getCls()

        then:
        closure instanceof Closure
    }

    def "test stageSteps - power cycle board"() {
        given:
        // Mock gauntlet 
        context.getStepExecutor() >> steps
        context.isDefault() >> false
        steps.getGauntEnv(_,_,_,_,_) >> getGauntEnv.call("NA","NA","NA","v0.31","NA")
        steps.isUnix() >> true
        steps.sh(script: 'uname', returnStdout: true) >> 'Linux'
        steps.fileExists('out.out') >> true
        steps.readFile('out.out') >> 'STDOUT of some successful nebula command'
        ContextRegistry.registerContext(context)
        Gauntlet gauntlet = new Gauntlet()
        gauntlet.construct("NA","NA","NA","NA","NA")
        
        def PowerCycleBoard = new PowerCycleBoard()
        String board = "zynq-zc702-adv7511-ad9361-fmcomms2-3"
        gauntlet.set_env("docker_args", [])
        gauntlet.set_env("debug_level", 3)

        when:
        PowerCycleBoard.stageSteps(gauntlet, board)

        then:
        1 * steps.sh(['script':'nebula update-config pdu-config pdu_type --board-name=zynq-zc702-adv7511-ad9361-fmcomms2-3', 'returnStdout':true])
        1 * steps.sh(['script':'nebula update-config pdu-config outlet --board-name=zynq-zc702-adv7511-ad9361-fmcomms2-3', 'returnStdout':true])
        1 * steps.sh(['script':'nebula pdu.power-cycle -b zynq-zc702-adv7511-ad9361-fmcomms2-3 -p  -o ', 'returnStdout':true])
        1 * steps.echo('[INFO] Running PowerCycleBoard for zynq-zc702-adv7511-ad9361-fmcomms2-3')
    }
}