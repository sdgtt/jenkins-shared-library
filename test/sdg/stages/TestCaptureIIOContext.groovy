package sdg.stages

import spock.lang.Specification
import sdg.Gauntlet
import sdg.Logger
import sdg.IStepExecutor
import sdg.ioc.*
import groovy.lang.GroovyShell

class TestCaptureIIOContext extends Specification {
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
        def CaptureIIOContext = new CaptureIIOContext()

        expect:
        CaptureIIOContext.getStageName() == "CaptureIIOContext"
    }

    def "test getCls"() {
        given:
        def CaptureIIOContext = new CaptureIIOContext()
        String board = "zynq-zc702-adv7511-ad9361-fmcomms2-3"

        when:
        def closure = CaptureIIOContext.getCls()

        then:
        closure instanceof Closure
    }
    
    def "test stageSteps - capture IIO context"() {
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

        def CaptureIIOContext = new CaptureIIOContext()
        String board = "zynq-zc702-adv7511-ad9361-fmcomms2-3"
        
        when:
        CaptureIIOContext.stageSteps(gauntlet, board)

        then:
        1 * steps.sh('git clone https://github.com/analogdevicesinc/libtinyiiod.git')
        1 * steps.sh('git clone -b v0.1.0 https://github.com/analogdevicesinc/iio-emu.git')
        1 * steps.sh(['script':'nebula update-config network-config dutip --board-name=zynq-zc702-adv7511-ad9361-fmcomms2-3', 'returnStdout':true])
        1 * steps.sh('xml_gen ip: > "zynq-zc702-adv7511-ad9361-fmcomms2-3.xml"')
        1 * steps.archiveArtifacts(['artifacts':'*.xml'])

    }
}