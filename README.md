<!DOCTYPE html>
<html>
<head>
<style>
table {
    font-family: arial, sans-serif;
    border-collapse: collapse;
    width: 100%;
}

td, th {
    border: 1px solid #4cd964;
    text-align: left;
    padding: 8px;
}

tr:nth-child(even) {
    background-color: #dddddd;
}
</style>
</head>
<body>
<h1>
Emoji-er
</h1>
<p>
The basic premise of this bot is to simply bring a standard nitro feature to all members of your server; use emojis from other servers.
This bot is used both for grabbing and sending the emojis, as adding it to your server and registering it in the list will allow your
servers emojis to be used with the bot in other peoples servers.

The command syntax for the bot is similar to that of the discord emoji syntax (:emojiname:) wher as the bot works as :emoji.command:

For sending emojies, instead, you'll need to do :[server].[emoticon]:,
you can use them exactly like normal emoticons
</p>


<h2>Simple Commands</h2>

<table>
  <tr>
    <th>Name</th>
    <th>Usage</th>
	<th>Description</th>
    <th colspan="2">Example</th>
  </tr>
  <tr>
    <td>help</td>
    <td>:emoji.help:</td>
    <td>Returns help</td>
	<td></td>
	<td><a href="https://imgur.com/KwS7QIO"><img src="https://i.imgur.com/KwS7QIO.png" title="source: imgur.com" /></a></td>
  </tr>
  <tr>
    <td>ping</td>
    <td>:emoji.ping:</td>
    <td>Returns pong</td>
	<td></td>
	<td><a href="https://imgur.com/j6Sk9kI"><img src="https://i.imgur.com/j6Sk9kI.png" title="source: imgur.com" /></a></td>
  </tr>
  <tr>
    <td>list</td>
    <td>:emoji.list: &lt;server&gt; </td>
    <td>The bot will respond with a list of usable emojis from the server</td>
	<td>
	<p>:emoji.list: fruit</p>
	<p>:emoji.list: ca</p>
	</td>
	<td><a href="https://imgur.com/mUZWZ8Q"><img src="https://i.imgur.com/mUZWZ8Q.png" title="source: imgur.com" /></a></td>
  </tr>
  <tr>
    <td>servers</td>
    <td>:emoji.servers:</td>
    <td>The bot will respond with a list of servers with emojis you can use</td>
	<td></td>
	<td><a href="https://imgur.com/67osOuE"><img src="https://i.imgur.com/67osOuE.png" title="source: imgur.com" /></a></td>
  </tr>
  <tr>
    <td>status</td>
    <td>:emoji.status:</td>
    <td>The bot will respond with your servers current settings</td>
	<td></td>
	<td><a href="https://imgur.com/Ob3NxYU"><img src="https://i.imgur.com/Ob3NxYU.png" title="source: imgur.com" /></a></td>
  </tr>
  <tr>
  </tr>
</table>

<h2>Admin Commands</h2>

<table>
  <tr>
    <th>Name</th>
    <th>Usage</th>
	<th>Description</th>
    <th colspan="2">Example</th>
  </tr>
  <tr>
    <td>modrole</td>
    <td>:emoji.modrole: {add,remove,clear,auto,list} [RoleMention]</td>
    <td>used to manage what roles can use admin commands</td>
	<td>
	<p>:emoji.modrole: add @coolguys</p>
	<p>:emoji.modrole: remove @coolguys</p>
	<p>:emoji.modrole: clear</p>
	<p>:emoji.modrole: auto</p>
	<p>:emoji.modrole: list</p>
	</td>
	<td>
	<p><a href="https://imgur.com/30vjN9W"><img src="https://i.imgur.com/30vjN9W.png" title="source: imgur.com" /></a></p>
	<p><a href="https://imgur.com/t6B4Csf"><img src="https://i.imgur.com/t6B4Csf.png" title="source: imgur.com" /></a></p>
	<p><a href="https://imgur.com/eUFaMoE"><img src="https://i.imgur.com/eUFaMoE.png" title="source: imgur.com" /></a></p>
	<p><a href="https://imgur.com/J46TVP7"><img src="https://i.imgur.com/J46TVP7.png" title="source: imgur.com" /></a></p>
	<p><a href="https://imgur.com/JioF32I"><img src="https://i.imgur.com/JioF32I.png" title="source: imgur.com" /></a></p>
	</td>
  </tr>
  <tr>
    <td>register</td>
    <td>:emoji.register: &lt;name&gt;</td>
    <td>registers your server to the list of servers that allow its emojis to be used in other servers.<br>The name must be a single word of less than 10 characters</td>
	<td>:emoji.register: fruit</td>
	<td><a href="https://imgur.com/nLrYZHV"><img src="https://i.imgur.com/nLrYZHV.png" title="source: imgur.com" /></a></td>
  </tr>
  <tr>
    <td>unregister</td>
    <td>:emoji.unregister:</td>
    <td>removes your server from the list of servers that allow its emojis to be used in other servers</td>
	<td></td>
	<td><a href="https://imgur.com/tPct8js"><img src="https://i.imgur.com/tPct8js.png" title="source: imgur.com" /></a></td>
  </tr>
  <tr>
    <td>toggle</td>
    <td>:emoji.toggle:</td>
    <td>toggles allowing all other servers emojis to be used in your server</td>
	<td></td>
	<td>
	<p><a href="https://imgur.com/E4vI7Wu"><img src="https://i.imgur.com/E4vI7Wu.png" title="source: imgur.com" /></a></p>
	<p><a href="https://imgur.com/tYmIbnY"><img src="https://i.imgur.com/tYmIbnY.png" title="source: imgur.com" /></a></p>
	</td>
  </tr>
  <tr>
    <td>enable</td>
    <td>:emoji.enable: &lt;server&gt;</td>
    <td>allows a specific servers emojis to be used in your server (theyre all on by default)</td>
	<td>
	<p>:emoji.enable: fruit</p>
	<p>:emoji.enable: ca</p>
	</td>
	<td><a href="https://imgur.com/eFV4sce"><img src="https://i.imgur.com/eFV4sce.png" title="source: imgur.com" /></a></td>
  </tr>
  <tr>
    <td>disable</td>
    <td>:emoji.disable: &lt;prefix&gt;</td>
    <td>disables a specific servers emojis to be used in your server</td>
	<td>
	<p>:emoji.disable: fruit</p>
	<p>:emoji.disable: ca</p>
	</td>
	<td><a href="https://imgur.com/1mIq8Ei"><img src="https://i.imgur.com/1mIq8Ei.png" title="source: imgur.com" /></a></td>
  </tr>
  <tr>
  </tr>
</table>

</body>
</html>
