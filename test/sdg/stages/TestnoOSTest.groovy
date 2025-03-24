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

    def "test DownloadBinaries - Xilinx boards"() {
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
        def board = "vc707_fmcomm2-3-vdemo"
        steps.sh(script: 'nebula update-config board-config example --board-name=vc707_fmcomm2-3-vdemo', returnStdout: true) >> "demo"
        steps.sh(script: 'nebula update-config downloader-config platform --board-name=vc707_fmcomm2-3-vdemo', returnStdout: true) >> "Xilinx"
        
        def bootgen = 'outs/ad9361_xilinx_demo_fmcomms2_vc707/bootgen_sysfiles.tar.gz'

        steps.sh(script: 'tar -xf '+bootgen)  >> 0
        steps.sh(script: "ls outs", returnStdout: true) >> "ad9361_xilinx_demo_fmcomms2_vc707"
        steps.sh(script: 'ls | grep *vc707.elf', returnStdout: true) >> "ad9361_xilinx_demo_fmcomms2_vc707.elf"
       

        when:
        def filepath = noOSTest.DownloadBinaries(gauntlet, board)

        then:
        1 * steps.sh('set -o pipefail; nebula show-log dl.bootfiles --board-name=vc707_fmcomm2-3-vdemo --source-root="/var/lib/tftpboot" --source=NA --branch="main" --filetype="noos" 2>&1 | tee out.out')
        //assert filepath == "ad9361_xilinx_demo_fmcomms2_vc707.elf"
    }

    // def "test DownloadBinaries - no file found"() {
    //     given:
    //     def noOSTest = new noOSTest()
    //     def gauntlet = new Gauntlet()
    //     def board = "invalid_board"

    //     when:
    //     def filepath = noOSTest.DownloadBinaries(gauntlet, board)

    //     then:
    //     filepath == ""
    // }

    // def "test DownloadBinaries - hyphenated boardname"() {
    //     given:
    //     def noOSTest = new noOSTest()
    //     def gauntlet = new Gauntlet()
    //     def board = ""

    //     when:
    //     def filepath = noOSTest.DownloadBinaries(gauntlet, board)

    //     then:
    //     filepath == ""
    // }

    // def "test RunTests - non-Xilinx boards"() {
    //     given:
    //     def noOSTest = new noOSTest()
    //     def gauntlet = new Gauntlet()
    //     def board = "max78000_adxl355"

    //     when:
    //     noOSTest.RunTests(gauntlet, board, "path/to/binaries")

    //     then:
    //     1 * steps.echo('[INFO] Running RunTests for max78000_adxl355')
    // }

    // def "test RunTests - Xilinx boards"() {
    //     given:
    //     def noOSTest = new noOSTest()
    //     def gauntlet = new Gauntlet()
    //     def board = "zcu102"

    //     when:
    //     noOSTest.RunTests(gauntlet, board, "path/to/binaries")

    //     then:
    //     1 * steps.echo('[INFO] Running RunTests for zcu102')
    // }

    // def "test RunTests - flash failed"() {
    //     given:
    //     def noOSTest = new noOSTest()
    //     def gauntlet = new Gauntlet()
    //     def board = "invalid_board"

    //     when:
    //     noOSTest.RunTests(gauntlet, board, "path/to/binaries")

    //     then:
    //     1 * steps.echo('[INFO] Running RunTests for invalid_board')
    // }
}

