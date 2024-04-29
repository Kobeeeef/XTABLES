function editJSONObject(obj, key, value) {
    const keys = key.split('.');
    let currentObj = obj;

    for (let i = 0; i < keys.length; i++) {
        const currentKey = keys[i];
        if (!currentObj[currentKey]) {
            // If the key doesn't exist, create a new object
            currentObj[currentKey] = {};
        }
        if (i === keys.length - 1) {
            // If it's the last key, update the value
            currentObj[currentKey].value = value;
        } else {
            // If it's not the last key, traverse deeper into the object
            if (!currentObj[currentKey].data) {
                currentObj[currentKey].data = {};
            }
            currentObj = currentObj[currentKey].data;
        }
    }

    return obj;
}

// Example usage:
const json = {
    "SmartDashboard": {
        "data": {
            "sometable": {
                "value": "Some Value3"
            }
        },
        "value": "Some Value6"
    },
    "SMartDashboard": {
        "value": "58"
    }
};

const updatedJson = editJSONObject(json, "SMartDashboard", "28");
console.log(JSON.stringify(updatedJson, null, 2));
