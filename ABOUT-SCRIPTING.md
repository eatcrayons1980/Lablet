# Scripting Support in Lablet

## History

Lablet supports user-created experiments through *experiment scripts*.
In the past, Lablet has required users to develop their experiment
scripts using the programming language Lua.

This version of Lablet continues supporting existing Lua scripts, while
also providing support for simplified scripts, written as `.tex` files.

## Basic Scripting Elements

Experiment scripts are constructed from basic elements supported by the
Lablet user interface. While attempts have been made to provide elements
which can be used for a variety of experiments, in some cases the limits
of the existing elements may be reached. In the future, it is hoped that
additional elements can be introduced, allowing greater flexibility when
designing Lablet experiments.

### Titles

Each experiment must have a title, or it may not be parsed correctly by
Lablet.

#### Adding a Title (Lua)

Adding a title in Lua is a bit lengthy, but straightforward.

```
Lablet = {
    interface = 1.0,
    title = "Scripting an Experiment in Lua"
}

function Lablet.buildActivity(builder)
    < all other Lua code goes here >
end
```

It is important to remember that all your code for Lablet sheets, text,
etc., must go within the function and before the `end` line.

#### Adding a Title (TeX)

In LaTeX, adding a title is easy; Lablet does most of the work for you.
And since most LaTeX documents already have a title, you just need to
tell Lablet where it's located.

```
% >>lablet:title
\title{Scripting an Experiment in LaTeX}
```

However, if you don't have a title for some reason (or you want to use a
different title), you can specify your own.

```
% >>lablet:title {My Lablet Title}
```

*Note:* All Lablet commands are comments in LaTeX and should not change
the contents of the document itself. This means your experiment handout
can also be your Lablet script!

### Lablet Sheet

A Lablet *sheet* defines one page (or view). Other Lablet elements are
usually placed within a Lablet sheet. Lablet supports a basic sheet, as
well as a few other specialised sheet types.

#### Scripting a Basic Sheet (Lua)

First you will assign the sheet object a name (*sheet*, in this case).
Then, you add the sheet to the experiment. Finally, you give the sheet a
title.

```
local sheet = builder:create("Sheet")
builder:add(sheet)
sheet:setTitle("Welcome To Lablet")
```

The variable `sheet` may be replaced with another name, but must be
consistent throughout the entire sheet.
The variable `builder` must be consistent across the entire Lua script.

#### Scripting a Basic Sheet (TeX)

In LaTeX, the coding part is handled for you. Just tell Lablet you want
a new sheet. Lablet will automatically look for the next curly-braced
string and use that as the title.

In this first example, we specify a title for our sheet by supplying our
own curly-braced string and the same line as the title command.

```
% >>lablet:sheet {Welcome To Lablet}
```

Or, we can simply place the command above a section header or other LaTeX
command that already has a curly-braced title.

```
% >>lablet:sheet
\section{Welcome To Lablet}
```

This works because, when a curly-braced text string is not found on the
same line as the sheet command, Lablet will look ahead a bit and try to
find a title on its own.

### Lablet Text

At a minimum, experiments will need text, so of course Lablet supports
text strings within sheets.

#### Scripting Text (Lua)

Simply pass text into the addText method for the desired sheet.

```
sheet:addText("This is the first line to display.")

-- Don't display this line.

sheet:addText("This is the second line to display.")
```

*Note:* In Lua, lines starting with two dashes are comments, and are not
processed.

#### Scripting Text (TeX)

In LaTeX, you might have several paragraphs, and you only want some of
them to be shown in Lablet.

```
% >>lablet:text
This is the first paragraph
to display in Lablet.

Don't display this paragraph
in Lablet; only display in
LaTeX.

% >>lablet:text
This is the second paragraph
to display in Lablet.

% >>lablet:text This line only displays in Lablet.
```

As you can see, there are a variety of ways to markup text in a `.tex`
file, depending on where you want it to be visible.

### Lablet Headers and Check Boxes

Headers and check boxes work in much the same way as text.

In Lua:

```
sheet:addHeader("This is a Header!")

sheet:addCheckQuestion("This displays a box for the user to check.")
```

In LaTeX:

```
% >>lablet:header
This is a Header!

% >>lablet:check
This displays a box for the user to check.
```

## More Documentation

Additional documentation will be added here in the future. Please
contact the Lablet team if you have any questions.
