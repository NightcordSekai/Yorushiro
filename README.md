# Yorushiro

> A simple chat bot base on the [Shiro](https://github.com/Mikuac/Shiro).

---


### Build & Run

```bash
./gradlew bootJar
java -jar build/libs/Yorushiro-1.0-SNAPSHOT.jar
```

## ➕ Adding a Command

```kotlin
class HelloCommand : ICommand {
    override val data = CommandData(
        name = "hello",
        description = "Say hello",
        usage = "hello",
        aliases = listOf("hi")
    )

    override fun process(bot: Bot, event: MessageEvent, args: Array<String>) {
        if (event is GroupMessageEvent) bot.replyGroupMsg(event, "ciallo!")
    }
}
```
Then invoke:

```kotlin
CommandManager.register(HelloCommand())
```

## 📄 License

MIT.

## WE ARE NOT RESIPONSIBLE FOR YOUR ACCOUNT.
