"use client"

import 'primereact/resources/themes/saga-blue/theme.css'; //theme
import 'primereact/resources/primereact.min.css'; //core css
import 'primeicons/primeicons.css';


import React, {useEffect, useRef, useState} from 'react';
import {DataTable} from 'primereact/datatable';
import {FilterMatchMode} from 'primereact/api';
import {Column} from 'primereact/column';
import Image from 'next/image'

import {Message} from 'primereact/message';


import { Tag } from 'primereact/tag';
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
    const [pingEvents, setPingEvents] = useState([]);
    const [pingDialogShown, setPingDialogShown] = useState(false);
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


        };
        TerminalService.on('command', commandHandler);

        return () => {
            TerminalService.off('command', commandHandler);
        };
    }, [socket]);

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
        return <InputText type="text" value={options.value} autoFocus={true}
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
                <Column field="value" header="" frozen={true} className="font-bold" editor={textEditor}
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
                    <input
                        type="text"
                        id="global_search"
                        placeholder="Name Search"
                        value={globalFilterValue}
                        onChange={onGlobalFilterChange}
                        className="block w-full py-2.5 text-gray-700 placeholder-gray-400/70 bg-white border border-gray-200 rounded-lg pl-11 pr-5 rtl:pr-11 rtl:pl-5 dark:bg-gray-900 dark:text-gray-300 dark:border-gray-600 focus:border-blue-400 dark:focus:border-blue-300 focus:ring-blue-300 focus:outline-none focus:ring focus:ring-opacity-40"
                    />
                </div>
            </div>
        </div>);
    };
    const header = renderHeader();

    return (<BlockUI blocked={loading}>


        <Menubar end={<>
            <Tag className="mr-2" icon={serverStatus === WebSocket.OPEN ? "pi pi-check" : serverStatus === WebSocket.CONNECTING ? "pi pi-info-circle" : serverStatus === WebSocket.CLOSING ? "pi pi-exclamation-triangle" : "pi pi-times"} severity={serverStatus === WebSocket.OPEN ? "success" : serverStatus === WebSocket.CONNECTING ? "info" : serverStatus === WebSocket.CLOSING ? "warning" : "danger"} value={serverStatus === WebSocket.OPEN ? "Server Connected" : serverStatus === WebSocket.CONNECTING ? "Connecting Server" : serverStatus === WebSocket.CLOSING ? "Server Disconnecting" : "Server Disconnected"}/>
            <Tag className="mr-2" icon={XTABLESState && serverStatus === WebSocket.OPEN ? "pi pi-check" : "pi pi-times"} severity={XTABLESState && serverStatus === WebSocket.OPEN ? "success" : "danger"} value={XTABLESState && serverStatus === WebSocket.OPEN ? "XTABLES Connected" : "XTABLES Disconnected"}/>
        </>} start={<img width={35} className="mr-2 rounded-xl" alt="logo" src={"/favicon.ico"}/>} model={[{
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
                const currentTime = `${new Date().getHours().toString().padStart(2, '0')}:${new Date().getMinutes().toString().padStart(2, '0')} ${(performance.now()).toFixed(2)} milliseconds`;
                setLoading(true)
                sendMessageAndWaitForCondition({type: "PING"}, (response) => response.type === "PING_RESPONSE").then(response => {
                    setLoading(false)
                    let value = JSON.parse(response.value);
                    let networkLatencyMS = value.networkLatencyMS;
                    let roundTripLatencyMS = value.roundTripLatencyMS;

                    // Calculate the time to reach the server
                    const timeToReachServer = new Date(Date.now() + roundTripLatencyMS - networkLatencyMS);
                    const timeToReachServerFormatted = `${timeToReachServer.getHours().toString().padStart(2, '0')}:${timeToReachServer.getMinutes().toString().padStart(2, '0')} ${(performance.now()).toFixed(2)} milliseconds`;

                    // Simulate server processing time (for demonstration)

                    const events = [{status: 'Sent', date: currentTime}, {
                        status: 'Processing',
                        date: timeToReachServerFormatted
                    }, {
                        status: 'Received',
                        date: `${new Date().getHours().toString().padStart(2, '0')}:${new Date().getMinutes().toString().padStart(2, '0')} ${(performance.now()).toFixed(2)} milliseconds`
                    },];
                    setPingEvents({
                        networkLatencyMS: networkLatencyMS, roundTripLatencyMS: roundTripLatencyMS, events: events
                    })
                    setPingDialogShown(true)

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
                    setLoading(false)
                });


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


        }]}/>
        <div className="flex bg-gray-200">
            <Dialog maximizable header="Ping Latency Statistics" visible={pingDialogShown} style={{width: '50vw'}}
                    onHide={() => setPingDialogShown(false)}>
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
                                        severity={pingEvents.networkLatencyMS < 0.4 ? "success" : pingEvents.networkLatencyMS < 0.6 ? "warn" : "error"}
                                        text={pingEvents.networkLatencyMS < 0.4 ? "GOOD" : pingEvents.networkLatencyMS < 0.6 ? "DELAYED" : "SLOW"}/>
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
                                        severity={pingEvents.roundTripLatencyMS < 0.9 ? "success" : pingEvents.roundTripLatencyMS < 1.5 ? "warn" : "error"}
                                        text={pingEvents.roundTripLatencyMS < 0.9 ? "GOOD" : pingEvents.roundTripLatencyMS < 1.5 ? "DELAYED" : "SLOW"}/>
                                </div>
                            </div>

                        </div>
                    </div>


                </div>
            </Dialog>
            <Splitter step={10} className={"w-screen"}>
                <SplitterPanel size={60} className="flex align-items-center justify-content-center">
                    <DataTable
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
                        emptyMessage="No Data Found"
                    >
                        <Column expander={allowExpansion} style={{width: '5rem'}}/>
                        <Column field="name" header="Name" sortable/>
                        <Column
                            field="value"
                            header="Value"
                            className="font-bold"
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
    </BlockUI>);
}
