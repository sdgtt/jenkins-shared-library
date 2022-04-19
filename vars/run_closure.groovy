def call(name, cls) {
    node(name) {
        cls.call()
    }
}
