# Ask and Autonomous

[简体中文](modes.md) | English

When Mika works for you, the mode decides which steps it checks with you first. There is one global switch: "Developer options" (开发者选项) → "Autonomous mode" (自主模式) in Settings. Off means Ask, on means Autonomous.

| Mode | Behavior |
|---|---|
| **Ask** (default) | The step that spends money is confirmed with you first: placing or submitting an order, or a confirm/submit while the page shows an amount due. The card says where, what and how much, and you can approve just this once or decline. The payment itself is done by you. Sending or deleting outside the allow list also needs a card. When you are away, on a schedule, or the task came from the computer, Mika keeps a draft and does not send. Packages on the deny list are refused with no card. |
| **Autonomous** | Does not ask even before paying. The spacing between items in batch jobs still applies. Sending and deleting outside the allow list still need a card; when you are away Mika still keeps a draft. |

- The switch applies to all tasks from then on; if a running task switches from Ask to Autonomous, payments are no longer asked about from the next step.
- In both modes Mika asks you to take over for things only you should do, like logging in, verification codes and payment passwords. It also asks when information is missing or only you can decide (e.g. which of two contacts with the same name).
- For tasks that run while you are away, such as scheduled tasks, Ask mode does not wait at a money-spending step: it skips it and tells you plainly which step was not done. Everything else is completed as usual.

You are responsible for actions you approve, and for actions Mika takes on your behalf in Autonomous mode. For anything important, use Ask mode and check the results; see the [disclaimer](disclaimer.en.md).
