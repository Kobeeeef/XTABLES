"use client"


import React, {useEffect, useRef, useState} from 'react';
import {DataTable} from 'primereact/datatable';
import {FilterMatchMode} from 'primereact/api';
import {Column} from 'primereact/column';

import {Dropdown} from 'primereact/dropdown';
import {MeterGroup} from 'primereact/metergroup';
import {Button} from 'primereact/button';
import {InputNumber} from 'primereact/inputnumber';
import {FloatLabel} from 'primereact/floatlabel';
import {IconField} from 'primereact/iconfield';
import {InputIcon} from 'primereact/inputicon';
import {Message} from 'primereact/message';
import {Tag} from 'primereact/tag';
import {InputText} from "primereact/inputtext";
import Logs from '../Components/Logs';
import validateKey from '../Utilities/KeyValidator'
import {Terminal} from 'primereact/terminal';
import {TerminalService} from 'primereact/terminalservice';
import {Dialog} from 'primereact/dialog';
import {Menubar} from 'primereact/menubar';
import {Splitter, SplitterPanel} from 'primereact/splitter';
import {Timeline} from 'primereact/timeline';
import {BlockUI} from 'primereact/blockui';
import Swal from 'sweetalert2'

export default function Main() {
    const dt = useRef(null);
    const [serverPort, setServerPort] = useState(null);
    const [serverAddress, setServerAddress] = useState(null);
    const [reconnectionTimeout, setReconnectionTimeout] = useState(null);
    const [settingsLoading, setSettingsLoading] = useState(false);
    const [pingEvents, setPingEvents] = useState([]);
    const pingDialogShown = useRef(false);
    const [pingDialogShownState, setPingDialogShownState] = useState(pingDialogShown.current);
    const [settingsDialogShown, setSettingsDialogShown] = useState(false);
    const [serverStatus, setServerStatus] = useState(3);
    const [rawJSON, setRawJSON] = useState({});
    const [XTABLESState, setXTABLESState] = useState(false);
    const [products, setProducts] = useState([]);
    const [expandedRows, setExpandedRows] = useState(null);
    const [globalFilterValue, setGlobalFilterValue] = useState('');
    const [socket, setSocket] = useState(null);
    const [loading, setLoading] = useState(true);
    const [messages, setMessages] = useState(["Waiting for message..."]);
    const [filters, setFilters] = useState({
        global: {value: null, matchMode: FilterMatchMode.CONTAINS},
        name: {value: null, matchMode: FilterMatchMode.STARTS_WITH},
        key: {value: null, matchMode: FilterMatchMode.STARTS_WITH}
    });
    const [theme, setTheme] = useState('Dark');

    useEffect(() => {
        const themeLink = document.getElementById('theme-css');
        if (themeLink) {
            if (theme.toLowerCase() === "light") themeLink.href = '/themes/lara-light-indigo/theme.css'; else themeLink.href = '/themes/lara-dark-indigo/theme.css';
        }
    }, [theme]);

    const helpMessage = `Available Commands: - clear: Clear the terminal screen. - put {key} {value}: Update a specific key value. - get {key}: Retrieve a value from the server. - sync: Syncs all data from server to refresh. - help: Show available commands and their descriptions.
`;

    useEffect(() => {
        function connect() {
            setLoading(true)
            const socket = new WebSocket('ws://localhost:8080/websocket');
            setServerStatus(socket.readyState)
            socket.onopen = () => {
                setServerStatus(socket.readyState)
                setSocket(socket)
                setLoading(false)
                console.log('WebSocket connection established');
            }
            socket.onmessage = (event) => {
                setServerStatus(socket.readyState)
                //let formatted = convertJSON(originalJSON);
                let parsed = JSON.parse(event.data);
                let parsedValue = JSON.parse(parsed.value);
                let type = parsed.type;
                if (type === "ALL") {
                    if (parsedValue == null) parsedValue = {};
                    setRawJSON(parsedValue)
                } else if (type === "STATUS") {
                    if (parsedValue === true) {
                        setLoading(false)
                        setXTABLESState(true)
                    } else {
                        setLoading(true)
                        setXTABLESState(false)
                    }
                } else if (type === "UPDATE") {
                    let key = parsedValue.key;
                    let value = parsedValue.value;
                    setRawJSON(prev => {
                        return editJSONObject(prev, key, value)
                    });
                } else if (type === "MESSAGES") {
                    if (parsedValue.length > 0) {
                        setMessages([...new Set(parsedValue)].reverse());
                    }
                }
            }
            socket.onerror = (error) => {
                setServerStatus(socket.readyState)
                console.error('WebSocket error:', error);
                socket.close()
            }
            socket.onclose = () => {
                setServerStatus(socket.readyState)
                setLoading(true)
                console.log('WebSocket connection closed');
                return connect();
            }
        }

        connect();
    }, []);

    useEffect(() => {
        const commandHandler = (text) => {
            let argsIndex = text.indexOf(' ');
            let command = argsIndex !== -1 ? text.substring(0, argsIndex) : text;
            let tokens = text.split(" ");
            if (XTABLESState && serverStatus) {
                switch (command) {
                    case 'help':
                    case 'ls':
                        TerminalService.emit('response', helpMessage);
                        break;
                    case 'clear':
                        TerminalService.emit('clear');
                        break;
                    case 'put':
                        if (!(tokens.length >= 3)) {
                            TerminalService.emit('response', "Invalid command usage!");
                        } else if (validateKey(tokens[1]) !== null) {
                            TerminalService.emit('response', validateKey(tokens[1]));
                        } else {
                            setLoading(true)
                            TerminalService.emit('response', "Sending put request...");
                            sendMessageAndWaitForCondition({
                                type: "UPDATE", value: tokens.slice(2).join(" "), key: tokens[1]
                            }, (response) => response.type === "UPDATE_RESPONSE").then(response => {
                                setLoading(false)
                                let value = response.value;
                                TerminalService.emit('response', "Server responded with: " + value);
                            }).catch(error => {
                                setLoading(false)
                                TerminalService.emit('response', "Failed to put data: " + error);
                            });
                        }
                        break;
                    case 'get':
                        if (!(tokens.length == 2)) {
                            TerminalService.emit('response', "Invalid command usage!");
                        } else if (validateKey(tokens[1]) !== null) {
                            TerminalService.emit('response', validateKey(tokens[1]));
                        } else {
                            TerminalService.emit('response', "Sending get request...");
                            sendMessageAndWaitForCondition({
                                type: "GET",
                                key: tokens[1]
                            }, (response) => response.type === "GET").then(response => {
                                let value = response.value;
                                TerminalService.emit('response', "" + value);
                            }).catch(error => {
                                TerminalService.emit('response', "Failed to get data: " + error);
                            });
                        }
                        break;
                    case 'sync':
                        setLoading(true)
                        TerminalService.emit('response', "Syncing data...");
                        sendMessageAndWaitForCondition({type: "ALL"}, (response) => response.type === "ALL").then(response => {
                            setLoading(false)
                            TerminalService.emit('response', "Data synced!");
                        }).catch(error => {
                            setLoading(false)
                            TerminalService.emit('response', "Failed to sync data: " + error);
                        });
                        break;
                    case 'ping':
                        TerminalService.emit('response', "Pinging server...");
                        sendMessageAndWaitForCondition({type: "PING"}, (response) => response.type === "PING_RESPONSE").then(response => {
                            let value = JSON.parse(response.value);
                            let networkLatencyMS = value.networkLatencyMS;
                            let roundTripLatencyMS = value.roundTripLatencyMS;
                            TerminalService.emit('response', `Network Latency = ${networkLatencyMS}ms; Round Trip Latency = ${roundTripLatencyMS}ms`);
                        }).catch(error => {
                            TerminalService.emit('response', "Failed to ping server: " + error);
                        });
                        break;
                    default:
                        TerminalService.emit('response', 'Unknown command: ' + command);
                        break;
                }
            } else {
                if (!XTABLESState) {
                    TerminalService.emit('response', "Connect to XTABLES server first!");
                } else {
                    TerminalService.emit('response', "Connect to backend server first!");
                }
            }


        };
        TerminalService.on('command', commandHandler);

        return () => {
            TerminalService.off('command', commandHandler);
        };
    }, [socket, serverStatus, XTABLESState]);

    function sendMessageAndWaitForCondition(message, conditionFunc, timeout = 1500) {

        socket.send(JSON.stringify(message));
        return new Promise((resolve, reject) => {
            const listener = event => {
                const data = JSON.parse(event.data);
                if (conditionFunc(data)) {
                    socket.removeEventListener('message', listener);
                    clearTimeout(timeoutId);
                    resolve(data);
                }
            };

            const timeoutId = setTimeout(() => {
                socket.removeEventListener('message', listener);
                reject(new Error('Timeout: Condition not met within ' + timeout + ' ms'));
            }, timeout);

            socket.addEventListener('message', listener);
        });
    }

    function editJSONObject(obj, key, value) {
        const keys = key.split('.');
        let currentObj = JSON.parse(JSON.stringify(obj));

        let temp = currentObj; // Keep a reference to the original object

        for (let i = 0; i < keys.length; i++) {
            const currentKey = keys[i];
            if (!temp[currentKey]) {
                // If the key doesn't exist, create a new object
                temp[currentKey] = {};
            }
            if (i === keys.length - 1) {
                // If it's the last key, update the value
                temp[currentKey].value = value;
            } else {
                // If it's not the last key, traverse deeper into the object
                if (!temp[currentKey].data) {
                    temp[currentKey].data = {};
                }
                temp = temp[currentKey].data; // Update the reference to the current level
            }
        }

        return currentObj; // Return the updated object
    }

    useEffect(() => {
        setProducts(convertJSON(rawJSON));
    }, [rawJSON]);

    function convertJSON(json) {
        const transformRecursively = (obj, parentKey = '') => {
            return Object.entries(obj).map(([key, value]) => {
                // Create a new object for the array
                let transformed = {
                    key: parentKey ? `${parentKey}.${key}` : key, name: key
                };

                if (typeof value === 'object' && value !== null) {
                    if (value.hasOwnProperty('value')) {
                        transformed.value = value.value;
                    }
                    // Recurse if there's nested data
                    if (value.data) {
                        transformed.data = transformRecursively(value.data, transformed.key);
                    }
                } else {
                    transformed.value = value;
                }

                return transformed;
            });
        };

        // Start the transformation with the top-level keys
        return transformRecursively(json);
    }


    const textEditor = (options) => {
        return <InputText type="text" value={options.value}
                          onChange={(e) => options.editorCallback(e.target.value)}
                          onKeyDown={(e) => e.stopPropagation()}/>;
    };

    const allowExpansion = (rowData) => {
        return rowData.data && Object.keys(rowData.data).length > 0;
    };
    const onCellEditComplete = (e) => {
        let {rowData, newValue, field, originalEvent: event} = e;

        if (field === "value") {
            let key = rowData.key;
            socket.send(JSON.stringify({type: "UPDATE", value: newValue, key: key}));
            Swal.fire({
                toast: true,
                title: 'Request Sent!',
                text: "The request is now queued!",
                showConfirmButton: false,
                timer: 3000,
                icon: "success",
                timerProgressBar: true,
                position: "top",
            });

        }
        return true;
    }

    const rowExpansionTemplate = (data) => {
        return (<div className="p-3">
            <DataTable showGridlines value={data.data} editMode={"cell"} expandedRows={expandedRows}
                       onRowToggle={(e) => setExpandedRows(e.data)}
                       rowExpansionTemplate={rowExpansionTemplate}
                       dataKey="key" removableSort>
                <Column expander={allowExpansion} style={{width: '5rem'}}/>
                <Column field="name" header="" sortable/>
                <Column field="value" header="" frozen={true}
                        className="font-bold max-w-1 overflow-hidden whitespace-nowrap" editor={textEditor}
                        onCellEditComplete={onCellEditComplete}
                        sortable/>
            </DataTable>
        </div>);
    };

    const onGlobalFilterChange = (e) => {
        const value = e.target.value;
        let _filters = {...filters};

        _filters['global'].value = value;

        setFilters(_filters);
        setGlobalFilterValue(value);
    };


    const renderHeader = () => {
        return (<div>


            <div>
                <div className="relative flex items-center mt-2">
        <span className="absolute">
        </span>

                    <IconField className={"block w-full"} iconPosition="left">
                        <InputIcon className="pi pi-search"> </InputIcon>
                        <InputText
                            className={"border border-gray-200 block w-full py-2.5 pl-11 pr-5 rtl:pr-11 rtl:pl-5 rounded-lg"}
                            type="text" id="global_search" value={globalFilterValue} placeholder="Name Search"
                            onChange={onGlobalFilterChange}/>
                    </IconField>
                </div>
            </div>
        </div>);
    };
    const header = renderHeader();
    return (
        <>
            <Menubar className={"rounded-none"} end={<>
                <Tag className="mr-2"
                     icon={serverStatus === WebSocket.OPEN ? "pi pi-check" : serverStatus === WebSocket.CONNECTING ? "pi pi-info-circle" : serverStatus === WebSocket.CLOSING ? "pi pi-exclamation-triangle" : "pi pi-times"}
                     severity={serverStatus === WebSocket.OPEN ? "success" : serverStatus === WebSocket.CONNECTING ? "info" : serverStatus === WebSocket.CLOSING ? "warning" : "danger"}
                     value={serverStatus === WebSocket.OPEN ? "Server Connected" : serverStatus === WebSocket.CONNECTING ? "Connecting Server" : serverStatus === WebSocket.CLOSING ? "Server Disconnecting" : "Server Disconnected"}/>
                <Tag className="mr-2"
                     icon={XTABLESState && serverStatus === WebSocket.OPEN ? "pi pi-check" : "pi pi-times"}
                     severity={XTABLESState && serverStatus === WebSocket.OPEN ? "success" : "danger"}
                     value={XTABLESState && serverStatus === WebSocket.OPEN ? "XTABLES Connected" : "XTABLES Disconnected"}/>
            </>} start={<img width={35} className="mr-2 rounded-xl" alt="logo" src={"/favicon.ico"}/>} model={[{
                label: 'Settings', icon: 'pi pi-cog', command: () => {

                    sendMessageAndWaitForCondition({type: "GET_SETTINGS"}, (a) => a.type === "GET_SETTINGS_RESPONSE")
                        .then((a) => {
                            let value = JSON.parse(a.value);
                            let SERVER_ADDRESS = value.SERVER_ADDRESS;
                            let SERVER_PORT = value.SERVER_PORT;
                            let RECONNECTION_TIMEOUT = value.RECONNECTION_TIMEOUT;
                            setServerAddress(SERVER_ADDRESS);
                            setServerPort(SERVER_PORT)
                            setReconnectionTimeout(RECONNECTION_TIMEOUT);
                            setSettingsDialogShown(true)
                        }).catch(() => {
                        Swal.fire({
                            toast: true,
                            title: 'Failed To Fetch!',
                            text: "The backend is offline or unresponsive!",
                            showConfirmButton: false,
                            timer: 3000,
                            icon: "error",
                            timerProgressBar: true,
                            position: "top",
                        });
                    })

                }
            },
                {
                    label: 'Sync', icon: 'pi pi-sync', command: () => {
                        setLoading(true)
                        socket.send(JSON.stringify({type: "ALL"}));
                        sendMessageAndWaitForCondition({type: "ALL"}, (a) => a.type === "ALL").then((a) => {
                            setLoading(false)
                            Swal.fire({
                                toast: true,
                                title: 'Data Synced!',
                                text: "The data was synced!",
                                showConfirmButton: false,
                                timer: 3000,
                                icon: "success",
                                timerProgressBar: true,
                                position: "top",
                            });
                        }).catch((e) => {
                            setLoading(false)
                            Swal.fire({
                                toast: true,
                                title: 'Failed To Sync!',
                                text: "The data could not be synced!",
                                showConfirmButton: false,
                                timer: 3000,
                                icon: "error",
                                timerProgressBar: true,
                                position: "top",
                            });
                        });
                    }
                }, {
                    label: 'Ping', icon: 'pi pi-server', command: () => {
                        pingShowDialog();

                        function pingShowDialog() {
                            const currentTime = `${new Date().getHours().toString().padStart(2, '0')}:${new Date().getMinutes().toString().padStart(2, '0')} ${(performance.now()).toFixed(2)} milliseconds`;
                            sendMessageAndWaitForCondition({type: "PING"}, (response) => response.type === "PING_RESPONSE").then(response => {
                                let value = JSON.parse(response.value);
                                let networkLatencyMS = value.networkLatencyMS;
                                let roundTripLatencyMS = value.roundTripLatencyMS;
                                const timeToReachServer = new Date(Date.now() + roundTripLatencyMS - networkLatencyMS);
                                const timeToReachServerFormatted = `${timeToReachServer.getHours().toString().padStart(2, '0')}:${timeToReachServer.getMinutes().toString().padStart(2, '0')} ${(performance.now()).toFixed(2)} milliseconds`;


                                const events = [{status: 'Sent', date: currentTime}, {
                                    status: 'Processing',
                                    date: timeToReachServerFormatted
                                }, {
                                    status: 'Received',
                                    date: `${new Date().getHours().toString().padStart(2, '0')}:${new Date().getMinutes().toString().padStart(2, '0')} ${(performance.now()).toFixed(2)} milliseconds`
                                },];
                                setPingEvents({
                                    networkLatencyMS: networkLatencyMS,
                                    roundTripLatencyMS: roundTripLatencyMS,
                                    systemStatistics: value.systemStatistics,
                                    events: events
                                })
                                pingDialogShown.current = true;
                                setPingDialogShownState(true)
                                setTimeout(() => {
                                    if (pingDialogShown.current) return pingShowDialog();
                                }, 150)

                            }).catch(error => {
                                Swal.fire({
                                    toast: true,
                                    title: 'Failed To Ping!',
                                    text: "The server is offline or unresponsive!",
                                    showConfirmButton: false,
                                    timer: 3000,
                                    icon: "error",
                                    timerProgressBar: true,
                                    position: "top",
                                });
                            });
                        }

                    }
                }, {
                    label: 'Reboot', icon: 'pi pi-refresh', command: () => {
                        Swal.fire({
                            title: "Are you sure?",
                            text: "This will reboot the XTABLES server!",
                            confirmButtonText: "Reboot",
                            showCancelButton: true,
                            reverseButtons: true,
                            cancelButtonColor: "#3085d6",
                            confirmButtonColor: "#d33",
                        }).then((result) => {
                            if (result.isConfirmed) {


                                sendMessageAndWaitForCondition({type: "REBOOT"}, (response) => response.type === "REBOOT_RESPONSE").then(response => {

                                    let value = JSON.parse(response.value);
                                    if (value == "OK") Swal.fire({
                                        toast: true,
                                        title: 'Reboot Command Sent!',
                                        text: "The server is now rebooting!",
                                        showConfirmButton: false,
                                        timer: 2000,
                                        icon: "success",
                                        timerProgressBar: true,
                                        position: "top",
                                    }); else Swal.fire({
                                        toast: true,
                                        title: 'Failed To Reboot!',
                                        text: "The server responded with: " + value,
                                        showConfirmButton: false,
                                        timer: 3000,
                                        icon: "error",
                                        timerProgressBar: true,
                                        position: "top",
                                    })
                                }).catch(error => {
                                    Swal.fire({
                                        toast: true,
                                        title: 'Failed To Reboot!',
                                        text: "The server did not respond in time!",
                                        showConfirmButton: false,
                                        timer: 3000,
                                        icon: "error",
                                        timerProgressBar: true,
                                        position: "top",
                                    })
                                });
                            }
                        });

                    }


                }, {
                    label: 'Utilities', icon: 'pi pi-hammer', items: [
                        {
                            label: "Export",
                            icon: "pi pi-file-export",
                            items: [
                                {
                                    label: "PDF",
                                    icon: "pi pi-file-pdf",
                                    command: () => {
                                        import('jspdf').then((jsPDF) => {
                                            import('jspdf-autotable').then(() => {
                                                const doc = new jsPDF.default(0, 0);

                                                doc.autoTable([{
                                                    title: "Key",
                                                    dataKey: "key"
                                                },
                                                    {
                                                        title: "Value",
                                                        dataKey: "value"
                                                    },
                                                    {
                                                        title: "Subtable",
                                                        dataKey: "data"
                                                    }], products);
                                                doc.save('XBOARD.pdf');
                                            });
                                        });
                                    }
                                },
                                {
                                    label: "EXCEL",
                                    icon: "pi pi-file-excel"
                                },
                                {
                                    label: "CSV",
                                    icon: "pi pi-file"
                                },
                            ]
                        }
                    ]
                }
            ]}/>
            <Dialog className={"w-[50vw]"} footer={<Button
                className={"px-6 py-2 font-medium tracking-wide text-white capitalize transition-colors duration-300 transform bg-blue-600 rounded-lg hover:bg-blue-500 focus:outline-none focus:ring focus:ring-blue-300 focus:ring-opacity-80"}
                label="Save" icon="pi pi-check" loading={settingsLoading}
                disabled={!serverStatus || !serverAddress || !serverPort || !reconnectionTimeout}
                onClick={(e) => {
                    setSettingsLoading(true)
                    sendMessageAndWaitForCondition({
                        type: "UPDATE_SETTINGS",
                        SERVER_ADDRESS: serverAddress,
                        SERVER_PORT: serverPort,
                        RECONNECTION_TIMEOUT: reconnectionTimeout
                    }, (a) => a.type === "UPDATE_SETTINGS_RESPONSE").then(response => {
                        setSettingsLoading(false)
                        setSettingsDialogShown(false)
                    }).catch((ignored) => {
                        setSettingsLoading(false)
                    })

                }}/>
            } maximizable header="XBOARD Settings"
                    visible={settingsDialogShown} onHide={() => {
                setSettingsDialogShown(false)
            }}>
                <div className="card p-fluid border border-gray-600 rounded-2xl shadow-xl p-4">
                    <h5 className={"mb-2 font-bold"}>SERVER SETTINGS</h5>
                    <div className={"space-y-2"}>
                        <div>
                            <label htmlFor="server_address">Server Address</label>
                            <InputText value={serverAddress}
                                       onChange={(e) => setServerAddress( e.target.value)} className={"h-10 pl-2"}
                                       id="server_address" type="text"/>
                        </div>
                        <div>
                            <label htmlFor="server_port">Server Port</label>
                            <InputNumber value={serverPort} onValueChange={(e) => setServerPort(e.value)}
                                         inputClassName={"pl-2"} useGrouping={false} min={0} max={65535}
                                         className={"h-10"} id="server_port"/>
                        </div>
                        <div>
                            <label htmlFor="reconnection_timeout">Reconnection Timeout</label>
                            <InputNumber value={reconnectionTimeout}
                                         onValueChange={(e) => setReconnectionTimeout(e.value)}
                                         inputClassName={"pl-2"} suffix={" milliseconds"} min={100} max={60000}
                                         className={"h-10"} id="name1"/>
                        </div>
                    </div>
                </div>
                <div className="card p-fluid border border-gray-600 rounded-2xl shadow-xl p-4 mt-4">
                    <h5 className={"mb-2 font-bold"}>XBOARD SETTINGS</h5>
                    <div className={"space-y-2"}>
                        <div>
                            <label htmlFor="name1">Server Address</label>
                            <InputText className={"h-11 pl-2"} id="name1" type="text"/>
                        </div>
                        <div>
                            <label htmlFor="name1">Server Port</label>
                            <InputNumber inputClassName={"pl-2"} useGrouping={false} min={0} max={65535}
                                         className={"h-11"} id="name1"/>
                        </div>
                        <div>
                            <label htmlFor="name1">Theme</label>
                            <Dropdown value={{name: theme}} onChange={(e) => setTheme(e.value.name)} id="name1"
                                      options={[{name: "Dark"}, {name: "Light"}]} optionLabel="name"
                                      placeholder="Select a theme" checkmark={true} highlightOnSelect={false}/>
                        </div>
                    </div>
                </div>

            </Dialog>
            <Dialog maximizable header="Ping Latency Statistics" visible={pingDialogShownState}
                    style={{width: '60vw'}}
                    onHide={() => {
                        pingDialogShown.current = false;
                        setPingDialogShownState(false)
                    }}>
                <Timeline value={pingEvents.events} opposite={(item) => item.status}
                          content={(item) => <small className="text-color-secondary">{item.date}</small>}/>
                <hr className="my-6 border-1 border-gray-200"/>

                <div className="grid grid-cols-2 justify-center">
                    <div className="card h-full">
                        <span className="font-semibold text-lg flex justify-center">Network Latency</span>
                        <div className="flex justify-center mt-1">
                            <div>
                                    <span
                                        className="text-4xl font-bold text-900 flex justify-center">{pingEvents.networkLatencyMS}</span>
                                <div className="flex justify-center mt-2">
                                    <Message
                                        severity={pingEvents.networkLatencyMS < 1 ? "success" : pingEvents.networkLatencyMS < 1.5 ? "warn" : "error"}
                                        text={pingEvents.networkLatencyMS < 1 ? "GOOD" : pingEvents.networkLatencyMS < 1.5 ? "DELAYED" : "SLOW"}/>
                                </div>
                            </div>
                        </div>
                    </div>

                    <div className="card h-full">
                        <span className="font-semibold flex justify-center text-lg">Round Trip Latency</span>
                        <div className="flex justify-center mt-1">
                            <div>
                                    <span
                                        className="text-4xl font-bold text-900 flex justify-center">{pingEvents.roundTripLatencyMS}</span>
                                <div className="flex justify-center mt-2">
                                    <Message
                                        severity={pingEvents.roundTripLatencyMS < 3 ? "success" : pingEvents.roundTripLatencyMS < 8 ? "warn" : "error"}
                                        text={pingEvents.roundTripLatencyMS < 3 ? "GOOD" : pingEvents.roundTripLatencyMS < 8 ? "DELAYED" : "SLOW"}/>
                                </div>
                            </div>

                        </div>
                    </div>


                </div>
                <hr className="my-6 border-1 border-gray-200"/>
                <div className="space-y-5">
                    <MeterGroup values={[{
                        label: 'Memory Usage',
                        icon: "pi pi-history",
                        color: '#ff0000',
                        value: pingEvents?.systemStatistics ? (pingEvents.systemStatistics.maxMemoryMB / pingEvents.systemStatistics.freeMemoryMB).toFixed(2) : "UNKNOWN"
                    }, {
                        label: 'Memory Free',
                        color: '#34d399',
                        icon: "pi pi-history",
                        value: pingEvents?.systemStatistics ? (100 - pingEvents.systemStatistics.maxMemoryMB / pingEvents.systemStatistics.freeMemoryMB).toFixed(2) : "UNKNOWN"
                    }]}/>
                    <MeterGroup values={[{
                        label: 'CPU Usage',
                        icon: "pi pi-microchip",
                        color: '#ff0000',
                        value: pingEvents?.systemStatistics ? pingEvents.systemStatistics.processCpuLoadPercentage.toFixed(2) : "UNKNOWN"
                    }, {
                        label: 'CPU Free',
                        color: '#34d399',
                        icon: "pi pi-microchip",
                        value: pingEvents?.systemStatistics ? (100 - pingEvents.systemStatistics.processCpuLoadPercentage).toFixed(2) : "UNKNOWN"
                    }]}/>
                    <MeterGroup values={[{
                        label: 'Total Clients',
                        icon: "pi pi-user",
                        color: '#ff0000',
                        value: pingEvents?.systemStatistics ? pingEvents.systemStatistics.totalClients : 0
                    }, {
                        label: 'Free Clients',
                        icon: "pi pi-user",
                        color: '#34d399',
                        value: pingEvents?.systemStatistics ? 100 - pingEvents.systemStatistics.totalClients : 0
                    }]}/>
                </div>
            </Dialog>
            <div className={"h-screen"}>
                <div className="flex bg-gray-200">
                    <Splitter step={10} className={"w-screen"}>
                        <SplitterPanel size={60} className="flex align-items-center justify-content-center">
                            <DataTable
                                ref={dt}
                                virtualScrollerOptions={{itemSize: 50}}
                                value={products}
                                showGridlines
                                editMode="cell"
                                globalFilterFields={['name', 'value']}
                                filters={filters}
                                removableSort
                                filterDisplay="row"
                                expandedRows={expandedRows}
                                loading={loading}
                                onRowToggle={(e) => setExpandedRows(e.data)}
                                rowExpansionTemplate={rowExpansionTemplate}
                                className="h-screen w-full"
                                dataKey="key"
                                header={header}
                                scrollable
                                scrollHeight={"80vh"}
                                tableStyle={{minWidth: '15rem'}}
                            >
                                <Column expander={allowExpansion} style={{width: '5rem'}}/>
                                <Column field="name" header="Name" sortable/>
                                <Column
                                    field="value"
                                    header="Value"
                                    className="font-bold max-w-1 overflow-hidden whitespace-nowrap"
                                    frozen={true}
                                    editor={textEditor}
                                    onCellEditComplete={onCellEditComplete}
                                    sortable
                                />
                            </DataTable>
                        </SplitterPanel>
                        <SplitterPanel size={40} className="flex-1 align-items-center justify-content-center">
                            <div className="flex-1">

                                <Terminal
                                    welcomeMessage="Welcome to XBOARD! Type help to begin."
                                    prompt="XTABLES $"
                                    pt={{
                                        root: 'bg-gray-900 text-white border-round h-[50vh]',
                                        prompt: 'text-gray-400 mr-2',
                                        command: 'text-primary-300',
                                        response: 'text-primary-300'
                                    }}
                                />

                                <Logs initialOutput={messages}></Logs>
                            </div>
                        </SplitterPanel>
                    </Splitter>
                </div>
            </div>
        </>);
}
