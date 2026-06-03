import json

# Load the full UI workflow JSON
with open('/Users/chuck/Documents/antigravity/happy-turing/Ernie Turbo 4bit.json', 'r') as f:
    data = json.load(f)

# The active node IDs in the 1-stage ERNIE workflow (excluding 759 FluxResolutionNode)
active_ids = {92, 94, 96, 103, 104, 105, 107, 745, 757, 760, 761, 777, 778}

# Filter nodes
cleaned_nodes = []
for node in data.get('nodes', []):
    if node.get('id') in active_ids:
        # If it's the EmptyFlux2LatentImage node, disconnect its width/height links so it uses standard widgets
        if node.get('id') == 92:
            for input_field in node.get('inputs', []):
                if input_field.get('name') in ('width', 'height'):
                    if 'link' in input_field:
                        del input_field['link']
        cleaned_nodes.append(node)

# Filter links
# Link format: [id, origin_node, origin_slot, target_node, target_slot, type]
cleaned_links = []
for link in data.get('links', []):
    if link and len(link) >= 5:
        origin_node = link[1]
        target_node = link[3]
        if origin_node in active_ids and target_node in active_ids:
            cleaned_links.append(link)

# Construct cleaned JSON
cleaned_data = {
    "id": data.get("id", "ernie-workflow-id"),
    "revision": data.get("revision", 0),
    "last_node_id": max(active_ids),
    "last_link_id": max([link[0] for link in cleaned_links]) if cleaned_links else 0,
    "nodes": cleaned_nodes,
    "links": cleaned_links,
    "groups": [],
    "config": {},
    "extra": {},
    "version": 0.4
}

# Write output
with open('/Users/chuck/Documents/antigravity/happy-turing/comfyui-android-app/app/src/main/assets/ernie_workflow_ui.json', 'w') as f:
    json.dump(cleaned_data, f, indent=2)

print(f"Workflow cleaned: kept {len(cleaned_nodes)} nodes and {len(cleaned_links)} links.")
