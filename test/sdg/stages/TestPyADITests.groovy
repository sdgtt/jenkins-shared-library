package sdg.stages

import spock.lang.Specification
import sdg.Gauntlet
import sdg.Logger
import sdg.IStepExecutor
import sdg.ioc.*
import groovy.lang.GroovyShell

class TestPyADITests extends Specification {

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
        def pyADITests = new PyADITests()

        expect:
        pyADITests.getStageName() == "PyADITests"
    }

    def "test getCls"() {
        given:
        def pyADITests = new PyADITests()
        String board = "zynq-zc702-adv7511-ad9361-fmcomms2-3"

        when:
        def closure = pyADITests.getCls()

        then:
        closure instanceof Closure
    }

    def "test PyADITests"() {
        given:
        String board = "zynq-zc702-adv7511-ad9361-fmcomms2-3"
        
        // Mock gauntlet 
        context.getStepExecutor() >> steps
        context.isDefault() >> false
        // Create gauntEnv with required properties
        def mockGauntEnv = getGauntEnv.call("NA","NA","NA","v0.31","NA")
        mockGauntEnv.pyadi_iio_branch = "main"
        mockGauntEnv.pyadi_iio_repo = "https://github.com/analogdevicesinc/pyadi-iio.git"
        
        steps.getGauntEnv(_,_,_,_,_) >> mockGauntEnv
        steps.isUnix() >> true
        steps.sh(script: 'uname', returnStdout: true) >> 'Linux'
        steps.fileExists('out.out') >> true
        steps.fileExists('testhtml/report.html') >> true
        steps.fileExists('testxml/zynq_zc702_adv7511_ad9361_fmcomms2_3_reports.xml') >> true
        steps.readFile('out.out') >> 'STDOUT of some successful nebula command'
        steps.retry(_, _) >> { int count, Closure cls -> cls.call() }
        steps.checkout(_) >> null
        
        // Mock dir operations with closure execution
        steps.dir(_, _) >> { String dirName, Closure closure -> 
            closure.call()
        }

        ContextRegistry.registerContext(context)
        Gauntlet gauntlet = new Gauntlet()
        gauntlet.construct("NA","NA","NA","v0.31","NA")
        def pyADITests = new PyADITests()

        when:
        pyADITests.stageSteps(gauntlet, board)

        then:
        1 * steps.sh('python3 -c "import ad9361"')
        1 * steps.sh(['script':'nebula update-config network-config dutip --board-name=zynq-zc702-adv7511-ad9361-fmcomms2-3', 'returnStdout':true])
        1 * steps.sh(['script':'python3 -m pytest --html=testhtml/report.html --junitxml=testxml/zynq_zc702_adv7511_ad9361_fmcomms2_3_reports.xml --adi-hw-map -v -k \'not stress and not prod\' -s --uri=ip: -m zynq_zc702_adv7511_ad9361_fmcomms2_3 --scan-verbose --capture=tee-sys', 'returnStatus':true])
        1 * steps.junit(['testResults':'pyadi-iio/testxml/*.xml', 'allowEmptyResults':true])
    }

    def "test PyADITests - no libad9361"() {
        given:
        String board = "zynq-zc702-adv7511-ad9361-fmcomms2-3"
        
        // Mock gauntlet 
        context.getStepExecutor() >> steps
        context.isDefault() >> false
        // Create gauntEnv with required properties
        def mockGauntEnv = getGauntEnv.call("NA","NA","NA","v0.31","NA")
        mockGauntEnv.pyadi_iio_branch = "main"
        mockGauntEnv.pyadi_iio_repo = "https://github.com/analogdevicesinc/pyadi-iio.git"
        
        steps.getGauntEnv(_,_,_,_,_) >> mockGauntEnv
        steps.isUnix() >> true
        steps.sh(script: 'uname', returnStdout: true) >> 'Linux'
        steps.fileExists('out.out') >> true
        steps.fileExists('testhtml/report.html') >> true
        steps.fileExists('testxml/zynq_zc702_adv7511_ad9361_fmcomms2_3_reports.xml') >> true
        steps.readFile('out.out') >> 'STDOUT of some successful nebula command'
        steps.retry(_, _) >> { int count, Closure cls -> cls.call() }
        steps.checkout(_) >> null
        steps.sh('python3 -c "import ad9361"') >> false
        
        // Mock dir operations with closure execution
        steps.dir(_, _) >> { String dirName, Closure closure -> 
            closure.call()
        }

        ContextRegistry.registerContext(context)
        Gauntlet gauntlet = new Gauntlet()
        gauntlet.construct("NA","NA","NA","v0.31","NA")
        def pyADITests = new PyADITests()

        when:
        pyADITests.stageSteps(gauntlet, board)

        then:
        1 * steps.sh('sudo cmake -DPYTHON_BINDINGS=ON ..')
        1 * steps.sh(['script':'python3 -m pytest --html=testhtml/report.html --junitxml=testxml/zynq_zc702_adv7511_ad9361_fmcomms2_3_reports.xml --adi-hw-map -v -k \'not stress and not prod\' -s --uri=ip: -m zynq_zc702_adv7511_ad9361_fmcomms2_3 --scan-verbose --capture=tee-sys', 'returnStatus':true])        
    }

    def "test PyADITests - serial uri"() {
        given:
        String board = "zynq-zc702-adv7511-ad9361-fmcomms2-3"
        
        // Mock gauntlet 
        context.getStepExecutor() >> steps
        context.isDefault() >> false
        // Create gauntEnv with required properties
        def mockGauntEnv = getGauntEnv.call("NA","NA","NA","v0.31","NA")
        mockGauntEnv.pyadi_iio_branch = "main"
        mockGauntEnv.pyadi_iio_repo = "https://github.com/analogdevicesinc/pyadi-iio.git"
        mockGauntEnv.iio_uri_source = "serial"

        steps.getGauntEnv(_,_,_,_,_) >> mockGauntEnv
        steps.isUnix() >> true
        steps.sh(script: 'uname', returnStdout: true) >> 'Linux'
        steps.fileExists('out.out') >> true
        steps.fileExists('testhtml/report.html') >> true
        steps.fileExists('testxml/zynq_zc702_adv7511_ad9361_fmcomms2_3_reports.xml') >> true
        steps.readFile('out.out') >> 'STDOUT of some successful nebula command'
        steps.retry(_, _) >> { int count, Closure cls -> cls.call() }
        steps.checkout(_) >> null
        steps.sh('python3 -c "import ad9361"') >> false
        
        // Mock dir operations with closure execution
        steps.dir(_, _) >> { String dirName, Closure closure -> 
            closure.call()
        }

        ContextRegistry.registerContext(context)
        Gauntlet gauntlet = new Gauntlet()
        gauntlet.construct("NA","NA","NA","v0.31","NA")
        def pyADITests = new PyADITests()

        when:
        pyADITests.stageSteps(gauntlet, board)

        then:
        1 * steps.sh(['script':'nebula update-config uart-config baudrate --board-name=zynq-zc702-adv7511-ad9361-fmcomms2-3', 'returnStdout':true])
        1 * steps.sh(['script':'python3 -m pytest --html=testhtml/report.html --junitxml=testxml/zynq_zc702_adv7511_ad9361_fmcomms2_3_reports.xml --adi-hw-map -v -k \'not stress and not prod\' -s --uri=serial:, -m zynq_zc702_adv7511_ad9361_fmcomms2_3 --scan-verbose --capture=tee-sys', 'returnStatus':true])
    }
}