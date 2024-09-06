# square-draw

A simple web design application that lets you draw rectangles on an html canvas, then select, reposition, and resize them.

## concepts

* A data structure to describe the file
* A renderer that builds a visual document from said file
* application state (tool choice, input lifecycle events)
* element picking (what's under the mouse)
* element selection, modification, resize handles

## TODO

- [x] set up app structure, tool bar, canvas
- [x] set up app state, tool mode selection on buttons
- [x] file structure planned out, hard code a test file
- [x] render fn
- [x] input loops, element drawing
- [x] element picking, selection and indication of selection
- [x] element translation (moving)
- [] element resizing, mouse icon hints
- [] editable fill color, show selected element's color

### stretch

- [] border size and color
- [] tool icons
- [] change element's z-index