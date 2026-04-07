Set shell = CreateObject("WScript.Shell")
scriptPath = CreateObject("Scripting.FileSystemObject").GetParentFolderName(WScript.ScriptFullName) & "\deploy-prod.ps1"
args = ""
For i = 0 To WScript.Arguments.Count - 1
    args = args & " """ & Replace(WScript.Arguments(i), """", """""") & """"
Next
shell.Run "powershell -ExecutionPolicy Bypass -File """ & scriptPath & """" & args, 1, True
