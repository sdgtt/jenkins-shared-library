def call(depends, hdlBranch, linuxBranch, firmwareVersion, bootfile_source) {
    def harness =  new sdg.Gauntlet()
    harness.construct(depends, hdlBranch, linuxBranch, firmwareVersion, bootfile_source)
    return harness
}
