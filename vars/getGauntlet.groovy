def call(depends, hdlBranch, linuxBranch, bootPartitionBranch="release", firmwareVersion, bootfile_source) {
    def harness =  new sdg.Gauntlet()
    harness.construct(depends, hdlBranch, linuxBranch, bootPartitionBranch, firmwareVersion, bootfile_source)
    return harness
}
