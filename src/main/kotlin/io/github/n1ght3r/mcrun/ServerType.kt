package io.github.n1ght3r.mcrun

/** Server software `runServer` can launch. [fillProject] is the project id on the Fill API. */
enum class ServerType(val fillProject: String) {
    PAPER("paper"),
    FOLIA("folia"),
}
