package sdg.stages

import spock.lang.Specification
import sdg.Gauntlet
import sdg.Logger
import sdg.IStepExecutor
import sdg.ioc.*
import groovy.lang.GroovyShell

class TestLibAD9361Tests extends Specification {

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
        def libAD9361Tests = new LibAD9361Tests()

        expect:
        libAD9361Tests.getStageName() == "LibAD9361Tests"
    }

    def "test getCls"() {
        given:
        def libAD9361Tests = new LibAD9361Tests()
        String board = "zynq-zc702-adv7511-ad9361-fmcomms2-3"

        when:
        def closure = libAD9361Tests.getCls()

        then:
        closure instanceof Closure
    }
    
    def "test stageSteps - supported board"() {
        given:
        String board = "zynq-zc702-adv7511-ad9361-fmcomms2-3"
        
        // Mock gauntlet 
        context.getStepExecutor() >> steps
        context.isDefault() >> false
        
        // Create gauntEnv with required properties
        def mockGauntEnv = getGauntEnv.call("NA","NA","NA","v0.31","NA")
        mockGauntEnv.libad9361_iio_branch = "main"
        mockGauntEnv.libad9361_iio_repo = "https://github.com/analogdevicesinc/libad9361-iio.git"
        
        steps.getGauntEnv(_,_,_,_,_) >> mockGauntEnv
        steps.isUnix() >> true
        steps.sh(script: 'uname', returnStdout: true) >> 'Linux'
        steps.fileExists('out.out') >> true
        steps.readFile('out.out') >> 'STDOUT of some successful nebula command'
        
        // Mock all shell commands that will be called
        steps.sh('mkdir -p build') >> null
        steps.sh('cmake -DPYTHON_BINDINGS=ON ..') >> null
        steps.sh('make') >> null
        steps.sh('make install') >> null
        steps.sh('ldconfig') >> null
        steps.sh({ String cmd -> cmd.contains('URI_AD9361') && cmd.contains('ctest') }) >> null
        steps.sh("mv Testing ${board}") >> null
        
        // Mock dir operations with closure execution
        steps.dir(_, _) >> { String dirName, Closure closure -> 
            closure.call()
        }
        
        // Mock other Jenkins pipeline steps
        steps.unstable(_) >> null
        steps.archiveArtifacts(_) >> null
        steps.xunit(_) >> null
        steps.CTest(_) >> { Map params -> params }
        
        ContextRegistry.registerContext(context)
        Gauntlet gauntlet = new Gauntlet()
        gauntlet.construct("NA","NA","NA","v0.31","NA")
        
        // Mock gauntlet methods using metaClass
        gauntlet.metaClass.run_i = { String cmd, boolean doRetry = false -> 
            return null  // Mock successful execution
        }
        
        def libAD9361Tests = new LibAD9361Tests()
        
        when:
        libAD9361Tests.stageSteps(gauntlet, board)

        then:
        1 * steps.sh(['script':'nebula update-config -s network-config -f dutip --board-name=zynq-zc702-adv7511-ad9361-fmcomms2-3', 'returnStdout':true])
        1 * steps.sh('URI_AD9361="ip:" ctest -T test --no-compress-output -V') // Verify test command is called
    }

    def "test stageSteps - unsupported board"() {
        given:
        String board = "zynq-zc706-adv7511-fmcomms11"
        
        // Mock gauntlet 
        context.getStepExecutor() >> steps
        context.isDefault() >> false
        
        // Create gauntEnv - for unsupported board test, we can use basic gauntEnv
        def mockGauntEnv = getGauntEnv.call("NA","NA","NA","v0.31","NA")
        
        steps.getGauntEnv(_,_,_,_,_) >> mockGauntEnv
        steps.isUnix() >> true
        steps.sh(script: 'uname', returnStdout: true) >> 'Linux'
        steps.fileExists('out.out') >> true
        steps.readFile('out.out') >> 'STDOUT of some successful nebula command'
        
        ContextRegistry.registerContext(context)
        Gauntlet gauntlet = new Gauntlet()
        gauntlet.construct("NA","NA","NA","v0.31","NA")
        
        // Mock the logger
        def logger = Mock(sdg.Logger)
        gauntlet.logger = logger

        def libAD9361Tests = new LibAD9361Tests()

        when:
        libAD9361Tests.stageSteps(gauntlet, board)

        then:
        1 * logger.info("LibAD9361Tests: Skipping board: "+board)
    }
}