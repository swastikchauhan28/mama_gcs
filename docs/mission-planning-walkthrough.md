# Mama GCS: plan a practice field

This walkthrough works without a rover connection. It creates a **local draft** and a modeled coverage preview; it does not upload a mission or move a rover. Use a disposable test installation for the practice values below.

**Protect an existing draft:** `New draft` clears the editor, including unsaved edits. `Save draft` replaces the saved working draft on that device. Before experimenting on a device with a route you want to keep, save it and use **File / Library → Export GeoJSON** to keep a separate file. Do not replace an existing draft unless you intend to.

## 1. Open a practice draft

1. Open **Mama GCS → Menu → Plan route**.
2. Tap **File / Library → New draft** and confirm **New draft**. This clears the editor; the previously saved draft is replaced only when you later tap **Save draft**.
3. In **File / Library**, tap **Rename**, enter `Practice field`, and tap **Apply**. The file menu can be closed using **Close files**.

## 2. Add route waypoints

These are example coordinates, not your farm. Enter decimal degrees with a decimal point. If your keyboard cannot enter a minus sign, use the **±** control beside the field.

| Point | Latitude | Longitude |
| --- | ---: | ---: |
| Waypoint 1 | -35.36326 | 149.16523 |
| Waypoint 2 | -35.36330 | 149.16527 |

1. Select the **Route** tab. Scroll down past the map if needed.
2. Tap **Add coordinates**, enter waypoint 1's latitude and longitude, then tap **Add**.
3. Repeat for waypoint 2. Check that both numbered waypoints appear in the intended order. Use **Edit**, **Up** or **Down** if needed.

The displayed distance is a straight-line estimate; obstacles and terrain are not checked.

## 3. Add the field outline

Select **Field outline**, scroll down and tap **Add outline coordinate** for each point in the order shown. Enter latitude and longitude, then tap **Add** each time.

| Outline point | Latitude | Longitude |
| --- | ---: | ---: |
| 1 | -35.36335 | 149.16520 |
| 2 | -35.36335 | 149.16535 |
| 3 | -35.36320 | 149.16535 |

Use **Move up** or **Move down** on a vertex to change its position in the outline. The first and last vertices cannot move beyond the ends. Changing the outline clears any existing coverage preview; generate it again after editing. Reordering can create a crossing, so review the outline before generating a preview.

These three points make a small triangular practice field. A real field needs its own accurately measured boundary; this outline is a local planning reference.

## 4. Preview coverage

1. Select **Coverage → Coverage settings**.
2. Set **Tool width (m)** to `2` and **Overlap (%)** to `10`. Leave **Headland width (m)** at `0` for this example and leave the optional error fields blank.
3. Tap **Generate preview** and wait for the calculation. Scroll down to see the field diagram and its explanation.
4. Review the shown working passes and any modeled unplanned or reserved areas. For the example, 2 m width and 10% overlap imply a nominal 1.8 m spacing between adjacent passes.

Use the implement's actual working width when planning a real field. Overlap is a planning assumption, not a guarantee: GPS accuracy, steering, tool position, obstacles and field edges affect real coverage. The preview shows separate modeled passes; **turns are not planned**, and the preview is **not saved as a route**. It cannot confirm that any soil has been ploughed or that the whole field will be covered.

To compare two alternatives, tap **Compare tool settings**, enter a width and overlap for options A and B, and tap **Compare**. Both use the current field, direction and headland. Review each option's modeled passes, length and unplanned range; overlapping ranges cannot establish which option has fewer gaps. To copy a valid option into **Coverage settings**, tap **Use option A** or **Use option B**. This clears old previews; tap **Coverage settings → Generate preview** to calculate again. Choosing an option does not save a route or send anything to a rover.

## 5. Save and export the local draft

1. Tap **Save draft**. Wait for **No unsaved changes**; the local draft now contains the route and field outline.
2. Open **File / Library → Export GeoJSON**. In Android's file picker, choose a folder such as **Downloads**, choose a suitable name and tap **Save**.
3. Keep the file where you can find it again. GeoJSON preserves the local outline as well as route waypoints. **Export GPX** carries route waypoints only, so it is not suitable for preserving the outline.

Saving or exporting the draft does not save the generated coverage preview as a route or send anything to the Cube.

## 6. Import the practice file

1. Open **File / Library → Import route file** and select the GeoJSON file you just exported.
2. Read **Import route preview**. Tap **Cancel** to keep your current editor, or tap **Replace working draft** to load the file into the editor. This replaces any unsaved editor changes.
3. Check the numbered route points and **Field outline**. The saved draft is still unchanged until you explicitly tap **Save draft**. For this practice, tap **Save draft** if you want to keep the imported copy as the saved draft.

You can generate the coverage preview again after importing. File export/import is local file exchange; **Upload**, **Download** and **Start mission** for the rover are unavailable in the current app.

## If something looks wrong

- **Add is unavailable:** check both coordinates are valid decimal numbers. Latitude must be between -90 and 90, longitude between -180 and 180.
- **No preview:** enter at least three distinct outline points, then check tool width and overlap. A valid field shape and settings are required.
- **The field or route is not your farm:** the numbers above are only a practice example. Replace them with independently checked coordinates for your own planning.
- **A file import would replace work you need:** tap **Cancel**. Export or save your current work before proceeding.

Physical navigation, tool control, accuracy, field coverage and safety require separate hardware and field validation. This guide does not establish that the rover is ready to operate.
