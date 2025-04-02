package sdg.stages

import spock.lang.Specification
import sdg.Gauntlet
import sdg.Logger
import sdg.IStepExecutor
import sdg.ioc.*
import groovy.lang.GroovyShell

class TestnoOStest extends Specification {

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
        def noOSTest = new noOSTest()

        expect:
        noOSTest.getStageName() == "noOSTest"
    }

    def "test getCls"() {
        given:
        def noOSTest = new noOSTest()
        String board = "max78000_adxl355"

        when:
        def closure = noOSTest.getCls()

        then:
        closure instanceof Closure
    } 

    def "test DownloadBinaries - non-Xilinx boards"() {
        given:
        //Mock gauntlet 
        context.getStepExecutor() >> steps
        context.isDefault() >> false
        steps.getGauntEnv(_,_,_,_,_) >> getGauntEnv.call("main","main","NA","v0.31","NA")
        steps.isUnix() >> true
        steps.sh(script: 'uname', returnStdout: true) >> 'Linux'
        steps.fileExists('out.out') >> true
        steps.readFile('out.out') >> 'STDOUT of some successful nebula command'
        ContextRegistry.registerContext(context)
        Gauntlet gauntlet = new Gauntlet()
        gauntlet.construct("main","main","NA","NA","NA")

        def noOSTest = new noOSTest()
        def board = "max78000_adxl355-vdummy_example"

        steps.sh(script: "ls outs", returnStdout: true) >> "eval-adxl355-pmdz_maxim_dummy_example_max78000_adxl355.elf"
        steps.sh(script: 'nebula update-config board-config example --board-name=max78000_adxl355-vdummy_example', returnStdout: true) >> "dummy_example"
        steps.sh(script: 'nebula update-config downloader-config platform --board-name=max78000_adxl355-vdummy_example', returnStdout: true) >> "maxim"

        when:
        def filepath = noOSTest.DownloadBinaries(gauntlet, board)

        then:
        1 * steps.sh('set -o pipefail; nebula show-log dl.bootfiles --board-name=max78000_adxl355-vdummy_example --source-root="/var/lib/tftpboot" --source=NA --branch="main" --filetype="noos" 2>&1 | tee out.out')
        assert filepath == "outs/eval-adxl355-pmdz_maxim_dummy_example_max78000_adxl355.elf"
    }

    def "test DownloadBinaries -no file found"() {
        given:
        //Mock gauntlet 
        context.getStepExecutor() >> steps
        context.isDefault() >> false
        steps.getGauntEnv(_,_,_,_,_) >> getGauntEnv.call("main","main","NA","v0.31","NA")
        steps.isUnix() >> true
        steps.sh(script: 'uname', returnStdout: true) >> 'Linux'
        steps.fileExists('out.out') >> true
        steps.readFile('out.out') >> 'STDOUT of some successful nebula command'
        ContextRegistry.registerContext(context)
        Gauntlet gauntlet = new Gauntlet()
        gauntlet.construct("main","main","NA","NA","NA")

        def noOSTest = new noOSTest()
        def board = "max78000_adxl355-vdummy_example"

        steps.sh(script: "ls outs", returnStdout: true) >> "eval-adxl355-pmdz_maxim_dummy_example.elf"
        steps.sh(script: 'nebula update-config board-config example --board-name=max78000_adxl355-vdummy_example', returnStdout: true) >> "dummy_example"
        steps.sh(script: 'nebula update-config downloader-config platform --board-name=max78000_adxl355-vdummy_example', returnStdout: true) >> "maxim"
        
        when:
        def filepath = noOSTest.DownloadBinaries(gauntlet, board)

        then:
        1 * steps.sh('set -o pipefail; nebula show-log dl.bootfiles --board-name=max78000_adxl355-vdummy_example --source-root="/var/lib/tftpboot" --source=NA --branch="main" --filetype="noos" 2>&1 | tee out.out')
        thrown Exception
    }

    def "test RunTests - non-Xilinx boards - flash failed"() {
        given:
        //Mock gauntlet 
        context.getStepExecutor() >> steps
        context.isDefault() >> false
        steps.getGauntEnv(_,_,_,_,_) >> getGauntEnv.call("main","main","NA","v0.31","NA")
        steps.isUnix() >> true
        steps.sh(script: 'uname', returnStdout: true) >> 'Linux'
        steps.fileExists('out.out') >> true
        steps.readFile('out.out') >> 'STDOUT of some successful nebula command'
        ContextRegistry.registerContext(context)
        Gauntlet gauntlet = new Gauntlet()
        gauntlet.construct("main","main","NA","NA","NA")

        def noOSTest = new noOSTest()
        def board = "max78000_adxl355-vdummy_example"

        steps.sh(script: 'nebula update-config board-config example --board-name=max78000_adxl355-vdummy_example', returnStdout: true) >> "dummy_example"
        steps.sh(script: 'nebula update-config downloader-config platform --board-name=max78000_adxl355-vdummy_example', returnStdout: true) >> "maxim"
        def filepath = "outs/eval-adxl355-pmdz_maxim_dummy_example_max78000_adxl355.elf"
        def jtag_cable_id = "123456"
        def flashStatus = 0


        when:
        noOSTest.RunTests(gauntlet, board, filepath)

        then:
        1 * steps.sh(['returnStatus':true, 'script':'./mcufla.sh outs/eval-adxl355-pmdz_maxim_dummy_example_max78000_adxl355.elf '])
        1 * steps.sh(['script':'nebula update-config jtag-config jtag_cable_id --board-name=max78000_adxl355-vdummy_example', 'returnStdout':true])
        thrown Exception
    }

    def "test RunTests - Xilinx boards - flash successful"() {
        given:
        //Mock gauntlet 
        context.getStepExecutor() >> steps
        context.isDefault() >> false
        steps.getGauntEnv(_,_,_,_,_) >> getGauntEnv.call("main","main","NA","v0.31","NA")
        steps.isUnix() >> true
        steps.sh(script: 'uname', returnStdout: true) >> 'Linux'
        steps.fileExists('out.out') >> true
        steps.readFile('out.out') >> 'STDOUT of some successful nebula command'
        ContextRegistry.registerContext(context)
        Gauntlet gauntlet = new Gauntlet()
        gauntlet.construct("main","main","NA","NA","NA")

        def noOSTest = new noOSTest()
        def board = "kcu105_adrv9371x-viio"

        steps.sh(script: 'nebula update-config board-config example --board-name=kcu105_adrv9371x-viio', returnStdout: true) >> "iio"
        steps.sh(script: 'nebula update-config downloader-config platform --board-name=kcu105_adrv9371x-viio', returnStdout: true) >> "Xilinx"
        def filepath = "ad9371_xilinx_iio_adrv9371x_kcu105.elf"

        when:
        noOSTest.RunTests(gauntlet, board, filepath)

        then:

        1 * steps.sh(['script':'nebula update-config downloader-config no_os_project --board-name=kcu105_adrv9371x-viio', 'returnStdout':true])
        1 * steps.sh(['script':'nebula update-config uart-config baudrate --board-name=kcu105_adrv9371x-viio', 'returnStdout':true])
        1 * steps.archiveArtifacts(['artifacts':'*-boot.log', 'followSymlinks':false, 'allowEmptyArchive':true])

    }
}

