def call(hdlBranch="NA", linuxBranch="NA", bootPartitionBranch="release",firmwareVersion="NA", bootfile_source="artifactory") {
    def harness =  new sdg.Gauntlet()
    harness.construct(hdlBranch, linuxBranch, bootPartitionBranch, firmwareVersion, bootfile_source)
    return harness
}
