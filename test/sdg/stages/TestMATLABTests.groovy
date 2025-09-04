package sdg.stages

import spock.lang.Specification
import sdg.Gauntlet
import sdg.Logger
import sdg.IStepExecutor
import sdg.ioc.*
import groovy.lang.GroovyShell

class TestMATLABTests extends Specification {

    def shell
    def getGauntEnv

    IStepExecutor steps
    IContext context

    def setup() {
        // mock the context
        shell = new GroovyShell()
        getGauntEnv = shell.parse(new File('vars/getGauntEnv.groovy'))

        steps = Mock(IStepExecutor.class)
        context = Mock(IContext.class)
    }

    def "test getStageName"() {
        given:
        def matlabTests = new MATLABTests()

        expect:
        matlabTests.getStageName() == "MATLABTests"
    }

    def "test getCls"() {
        given:
        def matlabTests = new MATLABTests()
        String board = "zynq-zc702-adv7511-ad9361-fmcomms2-3"

        when:
        def closure = matlabTests.getCls()

        then:
        closure instanceof Closure
    }

    def "test MATLABTests"() {
        given:
        String board = "zynq-zc702-adv7511-ad9361-fmcomms2-3"
        
        // Mock gauntlet 
        context.getStepExecutor() >> steps
        context.isDefault() >> false
        // Create gauntEnv with required properties
        def mockGauntEnv = getGauntEnv.call("NA","NA","NA","v0.31","NA")
        mockGauntEnv.matlab_branch = "master"
        mockGauntEnv.matlab_repo = "https://github.com/analogdevicesinc/TransceiverToolbox.git"
        mockGauntEnv.matlab_release = "R2021a"
        mockGauntEnv.matlab_timeout = "10m"
        mockGauntEnv.elastic_server = "localhost:9200"
        mockGauntEnv.matlab_commands = ["runHWTests('AD9361')"]

        steps.getGauntEnv(_,_,_,_,_) >> mockGauntEnv
        steps.isUnix() >> true
        steps.sh(script: 'uname', returnStdout: true) >> 'Linux'
        steps.fileExists('out.out') >> true
        steps.fileExists({ it.contains('_HWTestResults.xml') || it.contains('Results.xml') }) >> false  // No XML file exists for successful test
        steps.readFile('out.out') >> 'STDOUT of some successful nebula command'
        steps.retry(_, _) >> { int count, Closure cls -> cls.call() }
        steps.checkout(_) >> null
        steps.writeFile(_) >> null
        steps.junit(_) >> null
        
        // Mock the cp command for matlab setup
        steps.sh('cp -r /root/.matlabro /root/.matlab') >> null
        
        // Mock nebula command for getting IP
        steps.sh([script: 'nebula update-config network-config dutip --board-name=' + board, returnStdout: true]) >> '192.168.1.100'
        
        // Mock dir operations with closure execution
        steps.dir(_, _) >> { String dirName, Closure closure -> 
            closure.call()
        }

        ContextRegistry.registerContext(context)
        Gauntlet gauntlet = new Gauntlet()
        
        // Mock the getURIFromSerial method
        gauntlet.metaClass.getURIFromSerial = { String boardName ->
            return "serial:/dev/ttyACM0,115200"
        }
        
        // Mock the isMultiBranchPipeline method
        gauntlet.metaClass.isMultiBranchPipeline = { String repo ->
            return [isMultiBranch: false, branch: "master", ref: "+refs/heads/master:refs/remotes/origin/master"]
        }
        
        // Mock the nebula method
        gauntlet.metaClass.nebula = { String cmd ->
            if (cmd.contains('dutip')) {
                return '192.168.1.100'
            }
            return 'nebula output'
        }
        
        // Mock the createMFile method
        gauntlet.metaClass.createMFile = { ->
            // Do nothing, writeFile is already mocked
        }
        
        // Mock the parseForLogging method
        gauntlet.metaClass.parseForLogging = { String stage, String xmlFile, String boardName ->
            // Do nothing for testing
        }

        // Mock the logger
        def mockLogger = Mock(sdg.Logger)
        gauntlet.logger = mockLogger
        
        gauntlet.construct("NA","NA","NA","v0.31","NA")
        def matlabTests = new MATLABTests()

        when:
        matlabTests.stageSteps(gauntlet, board)

        then:
        // Verify that the matlab command with proper arguments was called and returns success (0)
        1 * steps.sh([script: { String cmd -> 
            cmd.contains('sudo -u user IIO_URI="ip:192.168.1.100"') &&
            cmd.contains('board="' + board + '"') &&
            cmd.contains('M2K_URI="serial:/dev/ttyACM0,115200"') &&
            cmd.contains('elasticserver=localhost:9200') &&
            cmd.contains('timeout -s KILL 10m') &&
            cmd.contains('/usr/local/MATLAB/R2021a/bin/matlab') &&
            cmd.contains('-r "run(\'matlab_commands.m\');exit"')
        }, returnStatus: true]) >> 0  // Return success status code
        
        // No unstable call should happen for successful test (status code 0)
        0 * steps.unstable(_)
    }

    def "test handleTestResult method - status code 1"() {
        given:
        def steps = Mock(sdg.IStepExecutor)
        def matlabTests = new MATLABTests()

        when:
        matlabTests.handleTestResult(steps, 1)

        then:
        1 * steps.unstable("MATLAB: Error encountered when running the tests.")
    }

    def "test handleTestResult method - status code 2"() {
        given:
        def steps = Mock(sdg.IStepExecutor)
        def matlabTests = new MATLABTests()

        when:
        matlabTests.handleTestResult(steps, 2)

        then:
        1 * steps.unstable("MATLAB: Some tests failed.")
    }

    def "test handleTestResult method - status code 3"() {
        given:
        def steps = Mock(sdg.IStepExecutor)
        def matlabTests = new MATLABTests()

        when:
        matlabTests.handleTestResult(steps, 3)

        then:
        1 * steps.unstable("MATLAB: Some tests did not run to completion.")
    }

    def "test handleTestResult method - status code 0"() {
        given:
        def steps = Mock(sdg.IStepExecutor)
        def matlabTests = new MATLABTests()

        when:
        matlabTests.handleTestResult(steps, 0)

        then:
        0 * steps.unstable(_)  // No unstable call should happen for status code 0
    }

}