package sdg.stages

import spock.lang.Specification
import sdg.Gauntlet
import sdg.Logger
import sdg.IStepExecutor
import sdg.ioc.*
import groovy.lang.GroovyShell

class TestLinuxTests extends Specification {

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
        LinuxTests linuxTestsStage = new LinuxTests()

        expect:
        linuxTestsStage.getStageName() == "LinuxTests"
    }

    def "test getCls"() {
        given:
        LinuxTests linuxTestsStage = new LinuxTests()
        String board = "pluto"

        when:
        def closure = linuxTestsStage.getCls()

        then:
        closure instanceof Closure
    }

    def "test stageSteps"(){
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

        LinuxTests linuxTestsStage = new LinuxTests()
        String board = "pluto"
        gauntlet.set_env("docker_args", [])
        gauntlet.set_env("debug_level", 3)

        steps.sh(script: 'cat dmesg_err_filtered.log | wc -l', returnStdout: true) >> "0"
        steps.sh(script: 'cat dmesg_warn.log | wc -l', returnStdout: true) >> "0"
        steps.sh(script: 'nebula show-log update-config driver-config iio_device_names -b pluto', 
                 returnStdout: true) >> '["adm1177-iio","ad9361-phy","cf-ad9361-dds-core-lpc","cf-ad9361-lpc"]'
        
        when:
        linuxTestsStage.stageSteps(gauntlet, board)

        then:
        1 * steps.echo('[INFO] Running LinuxTests for ' + board)
        1 * steps.sh('set -o pipefail; nebula show-log driver.check-iio-devices --uri="ip:" --board-name=pluto 2>&1 | tee out.out')
        assert gauntlet.get_elastic_field("pluto", "drivers_enumerated") == "4"
        assert gauntlet.get_elastic_field("pluto", "dmesg_errs") == "0"
        assert gauntlet.get_elastic_field("pluto", "dmesg_warns") == "0"
    }

    def "test stageSteps - missing devices"(){
        given:
        
        // Mock gauntlet 
        context.getStepExecutor() >> steps
        context.isDefault() >> false
        steps.getGauntEnv(_,_,_,_,_) >> getGauntEnv.call("NA","NA","NA","v0.31","NA")
        steps.isUnix() >> true
        steps.sh(script: 'uname', returnStdout: true) >> 'Linux'
        steps.fileExists('out.out') >> true
        steps.readFile('out.out') >> new File('test/resources/nebula_failures/pluto_iio_devices_out_fail.out').text
        ContextRegistry.registerContext(context)
        Gauntlet gauntlet = new Gauntlet()
        gauntlet.construct("NA","NA","NA","NA","NA")

        LinuxTests linuxTestsStage = new LinuxTests()
        String board = "pluto"
        gauntlet.set_env("docker_args", [])
        gauntlet.set_env("debug_level", 3)

        steps.sh(script: 'cat dmesg_err_filtered.log | wc -l', returnStdout: true) >> "0"
        steps.sh(script: 'cat dmesg_warn.log | wc -l', returnStdout: true) >> "0"
        steps.sh('set -o pipefail; nebula show-log driver.check-iio-devices --uri="ip:" --board-name=pluto 2>&1 | tee out.out') >> { throw new Exception() }
        steps.sh(script: 'nebula show-log update-config driver-config iio_device_names -b pluto', 
                 returnStdout: true) >> '["adm1177-iio","ad9361-phy","cf-ad9361-dds-core-lpc","cf-ad9361-lpc"]'
        

        when:
        linuxTestsStage.stageSteps(gauntlet, board)

        then:
        1 * steps.echo('[INFO] Running LinuxTests for ' + board)
        assert gauntlet.get_elastic_field("pluto", "drivers_enumerated") == "4"
        assert gauntlet.get_elastic_field("pluto", "drivers_missing") == "1"
        assert gauntlet.get_elastic_field("pluto", "dmesg_errs") == "0"
        assert gauntlet.get_elastic_field("pluto", "dmesg_warns") == "0"
    }


    def "test stageSteps - non-pluto"(){
        given:
        
        // Mock gauntlet 
        context.getStepExecutor() >> steps
        context.isDefault() >> false
        steps.getGauntEnv(_,_,_,_,_) >> getGauntEnv.call("NA","NA","NA","NA","artifactory")
        steps.isUnix() >> true
        steps.sh(script: 'uname', returnStdout: true) >> 'Linux'
        steps.fileExists('out.out') >> true
        steps.readFile('out.out') >> 'STDOUT of some successful nebula command'
        ContextRegistry.registerContext(context)
        Gauntlet gauntlet = new Gauntlet()
        gauntlet.construct("NA","NA","NA","NA","artifactory")

        LinuxTests linuxTestsStage = new LinuxTests()
        String board = "zynq-zc702-adv7511-ad9361-fmcomms2-3"
        gauntlet.set_env("docker_args", [])
        gauntlet.set_env("debug_level", 3)

        steps.sh(script: 'cat dmesg_err_filtered.log | wc -l', returnStdout: true) >> "0"
        steps.sh(script: 'cat dmesg_warn.log | wc -l', returnStdout: true) >> "0"
        steps.sh(script: 'nebula show-log update-config driver-config iio_device_names -b zynq-zc702-adv7511-ad9361-fmcomms2-3', 
                 returnStdout: true) >> '["ad7291","ad9361-phy","cf-ad9361-dds-core-lpc","cf-ad9361-lpc"]'
        steps.sh(['script':'nebula update-config board-config serial --board-name=zynq-zc702-adv7511-ad9361-fmcomms2-3', 
                  'returnStdout':true]) >> { throw new Exception() }

        when:
        linuxTestsStage.stageSteps(gauntlet, board)

        then:
        1 * steps.echo('[INFO] Running LinuxTests for ' + board)
        1 * steps.sh('set -o pipefail; nebula show-log driver.check-iio-devices --uri="ip:" --board-name=zynq-zc702-adv7511-ad9361-fmcomms2-3 2>&1 | tee out.out')
        1 * steps.sh('set -o pipefail; nebula show-log net.run-diagnostics --ip=\'\' --board-name=zynq-zc702-adv7511-ad9361-fmcomms2-3 2>&1 | tee out.out')
        
        assert gauntlet.get_elastic_field("zynq-zc702-adv7511-ad9361-fmcomms2-3", "drivers_enumerated") == "4"
        assert gauntlet.get_elastic_field("zynq-zc702-adv7511-ad9361-fmcomms2-3", "dmesg_errs") == "0"
        assert gauntlet.get_elastic_field("zynq-zc702-adv7511-ad9361-fmcomms2-3", "dmesg_warns") == "0"
    }

    def "test stageSteps - non-pluto w/ failed dmesg"(){
        given:
        
        // Mock gauntlet 
        context.getStepExecutor() >> steps
        context.isDefault() >> false
        steps.getGauntEnv(_,_,_,_,_) >> getGauntEnv.call("NA","NA","NA","NA","artifactory")
        steps.isUnix() >> true
        steps.sh(script: 'uname', returnStdout: true) >> 'Linux'
        steps.fileExists('out.out') >> true
        steps.readFile('out.out') >> 'STDOUT of some successful nebula command'
        ContextRegistry.registerContext(context)
        Gauntlet gauntlet = new Gauntlet()
        gauntlet.construct("NA","NA","NA","NA","artifactory")

        LinuxTests linuxTestsStage = new LinuxTests()
        String board = "zynq-zc702-adv7511-ad9361-fmcomms2-3"
        gauntlet.set_env("docker_args", [])
        gauntlet.set_env("debug_level", 3)

        steps.sh(script: 'cat dmesg_err_filtered.log | wc -l', returnStdout: true) >> "1"
        steps.sh(script: 'cat dmesg_warn.log | wc -l', returnStdout: true) >> "1"
        steps.sh(script: 'nebula show-log update-config driver-config iio_device_names -b zynq-zc702-adv7511-ad9361-fmcomms2-3', 
                 returnStdout: true) >> '["ad7291","ad9361-phy","cf-ad9361-dds-core-lpc","cf-ad9361-lpc"]'
        steps.sh(['script':'nebula net.check-dmesg --ip=\'\' --board-name=zynq-zc702-adv7511-ad9361-fmcomms2-3', 
                  'returnStdout':true]) >> { throw new Exception() }
        steps.sh(['script':'nebula update-config board-config serial --board-name=zynq-zc702-adv7511-ad9361-fmcomms2-3', 
                  'returnStdout':true]) >> { throw new Exception() }

        when:
        linuxTestsStage.stageSteps(gauntlet, board)

        then:        
        assert gauntlet.get_elastic_field("zynq-zc702-adv7511-ad9361-fmcomms2-3", "dmesg_errs") == "1"
        assert gauntlet.get_elastic_field("zynq-zc702-adv7511-ad9361-fmcomms2-3", "dmesg_warns") == "1"
        1 * steps.unstable('Linux Tests Failed: [dmesg check failed: null]')
    }

    def "test stageSteps - non pluto w/ failed diagnostics"(){
        given:
        
        // Mock gauntlet 
        context.getStepExecutor() >> steps
        context.isDefault() >> false
        steps.getGauntEnv(_,_,_,_,_) >> getGauntEnv.call("NA","NA","NA","NA","artifactory")
        steps.isUnix() >> true
        steps.sh(script: 'uname', returnStdout: true) >> 'Linux'
        steps.fileExists('out.out') >> true
        steps.readFile('out.out') >> 'STDOUT of some successful nebula command'
        ContextRegistry.registerContext(context)
        Gauntlet gauntlet = new Gauntlet()
        gauntlet.construct("NA","NA","NA","NA","artifactory")

        LinuxTests linuxTestsStage = new LinuxTests()
        String board = "zynq-zc702-adv7511-ad9361-fmcomms2-3"
        gauntlet.set_env("docker_args", [])
        gauntlet.set_env("debug_level", 3)

        steps.sh(script: 'cat dmesg_err_filtered.log | wc -l', returnStdout: true) >> "0"
        steps.sh(script: 'cat dmesg_warn.log | wc -l', returnStdout: true) >> "0"
        steps.sh(script: 'nebula show-log update-config driver-config iio_device_names -b zynq-zc702-adv7511-ad9361-fmcomms2-3', 
                 returnStdout: true) >> '["ad7291","ad9361-phy","cf-ad9361-dds-core-lpc","cf-ad9361-lpc"]'
        steps.sh(['script':'nebula update-config board-config serial --board-name=zynq-zc702-adv7511-ad9361-fmcomms2-3', 
                  'returnStdout':true]) >> { throw new Exception() }
        steps.sh('set -o pipefail; nebula show-log net.run-diagnostics --ip=\'\' --board-name=zynq-zc702-adv7511-ad9361-fmcomms2-3 2>&1 | tee out.out') >> { throw new Exception() }
        
        when:
        linuxTestsStage.stageSteps(gauntlet, board)

        then:
        1 * steps.echo('[INFO] Running LinuxTests for ' + board)
        1 * steps.sh('set -o pipefail; nebula show-log driver.check-iio-devices --uri="ip:" --board-name=zynq-zc702-adv7511-ad9361-fmcomms2-3 2>&1 | tee out.out')
        1 * steps.unstable('Linux Tests Failed:  [diagnostics failed: nebula failed]')
        
        assert gauntlet.get_elastic_field("zynq-zc702-adv7511-ad9361-fmcomms2-3", "drivers_enumerated") == "4"
        assert gauntlet.get_elastic_field("zynq-zc702-adv7511-ad9361-fmcomms2-3", "dmesg_errs") == "0"
        assert gauntlet.get_elastic_field("zynq-zc702-adv7511-ad9361-fmcomms2-3", "dmesg_warns") == "0"
    }
}