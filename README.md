# cache2local
Hacky script that turns SBT cache into local repo.
**You may lose all your files, so use it at your own risk!**

You must have Scala interpreter on your path. To validate: open the console, type `scala`, and hit enter. Please refer to Scala documentation for further details.
Run `script/offline` to transform ivy cache into local repo.
Run `script/online` to undo changes.

The script is highly dependent on the default Ivy layout with SBT. It will need tweaking for custom layouts.
