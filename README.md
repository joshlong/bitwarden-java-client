# Easy Bitwarden CLI Java Client and Autoconfiguration.

This is a convenient wrapper to make reading values from the Bitwarden CLI easier. It is <em>not a full client</em>. It
barely even handles the read case correctly. 

## Usage

I imagine folks will use this in slightly more involved scripts. 

Did you know you could create single-source code file Java programs using, e.g., [JBang.dev](https://jbang.dev)? Those
scripts can have dependencies and so on. So, in a shell program, get hold of the `BW_SESSION`:

```shell
export BW_SESSION="$(bw unlock --raw)"
```

Then call a "Java script" written using the Bitwarden CLI and [this starter](https://github.com/joshlong/bitwarden-cli-client-spring-boot-starter).

```shell
source $( ./cli.java ) 
```

